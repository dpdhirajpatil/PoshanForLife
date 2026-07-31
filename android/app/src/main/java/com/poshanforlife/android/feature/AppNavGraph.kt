package com.poshanforlife.android.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.fcm.DeepLinkTarget
import com.poshanforlife.android.feature.admin.adminGraph
import com.poshanforlife.android.feature.auth.AuthUiState
import com.poshanforlife.android.feature.auth.AuthViewModel
import com.poshanforlife.android.feature.auth.authGraph
import com.poshanforlife.android.feature.lead.leadGraph
import com.poshanforlife.android.feature.notifications.NotificationPermissionRequester
import com.poshanforlife.android.feature.patient.patientGraph
import com.poshanforlife.android.feature.practitioner.practitionerGraph
import com.poshanforlife.android.ui.components.LoadingScreen
import com.poshanforlife.android.ui.theme.PoshanPatientTheme

/**
 * Root NavHost. AuthViewModel is created once here (scoped to whichever
 * ViewModelStoreOwner hosts this composable — the Activity, since AppNavGraph
 * itself isn't a nav destination) and shared with the auth graph, so a login
 * triggered from LoginScreen and the graph-switch decision below observe the
 * exact same StateFlow.
 *
 * The switch itself: Loading -> stay on the loading screen; LoggedOut -> auth
 * graph; LoggedIn(role) -> that role's graph. This also fires on a role graph
 * -> LoggedOut transition (e.g. a failed token refresh clearing DataStore —
 * see TokenAuthenticator), which is why popUpTo(0) is used instead of a fixed
 * route: whichever graph is currently showing gets fully cleared, not just
 * the initial loading screen.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val navGraphViewModel: AppNavGraphViewModel = hiltViewModel()

    // Re-fetches GET /users/me on every app resume — the only way to notice a
    // staff-side LEAD->PATIENT conversion (AN-14's convert flow), since
    // nothing pushes that change to the device otherwise. AuthViewModel's
    // uiState re-derives from the refreshed cached user automatically, which
    // the LaunchedEffect below picks up like any other role change.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) authViewModel.refreshUser()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // AN-22: tracks the role seen on the *previous* authState transition (in-memory only,
    // reset on process death) — only a LEAD->PATIENT transition observed live, not a cold
    // start already at PATIENT, should ever show ConversionWelcomeScreen.
    var previousRole by remember { mutableStateOf<Role?>(null) }

    LaunchedEffect(authState) {
        val state = authState
        val target = when (state) {
            AuthUiState.Loading -> null
            AuthUiState.LoggedOut -> RootRoutes.AUTH_GRAPH
            is AuthUiState.LoggedIn -> {
                // Fire-and-forget FCM token push — covers "app was offline when the
                // token last rotated" retries, once per login/app-open (see AN-08).
                navGraphViewModel.syncFcmToken()
                val justConverted = previousRole == Role.LEAD && state.role == Role.PATIENT
                val route = when (state.role) {
                    Role.PATIENT ->
                        if (justConverted && !navGraphViewModel.hasSeenConversionWelcome(state.userId)) {
                            RootRoutes.CONVERSION_WELCOME
                        } else {
                            RootRoutes.PATIENT_GRAPH
                        }
                    Role.DOCTOR -> RootRoutes.PRACTITIONER_GRAPH
                    Role.ADMIN -> RootRoutes.ADMIN_GRAPH
                    Role.LEAD -> RootRoutes.LEAD_GRAPH
                    // Unrecognized role (backend enum drift): fail safe to logged-out, never crash.
                    Role.UNKNOWN -> RootRoutes.AUTH_GRAPH
                }
                previousRole = state.role
                route
            }
        }
        if (state is AuthUiState.LoggedOut) previousRole = null
        if (target != null && navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Resolves a tapped-push/bell-dropdown deep link into a route reachable from
    // this outer navController. Only patient/reports/{id} exists as an outer,
    // cross-role-reachable detail route today — practitioner/admin/lead's
    // notification-relevant screens (patient detail, lead detail) are still
    // tab-nested placeholders owned by each RoleScaffold's own inner NavHost,
    // which this navController can't reach; extend resolveDeepLinkRoute once a
    // future prompt adds a real outer-level detail route for those roles.
    LaunchedEffect(Unit) {
        navGraphViewModel.deepLinks.collect { target ->
            val role = (authState as? AuthUiState.LoggedIn)?.role ?: return@collect
            resolveDeepLinkRoute(role, target)?.let { route -> navController.navigate(route) }
        }
    }

    NavHost(navController = navController, startDestination = RootRoutes.LOADING) {
        composable(RootRoutes.LOADING) {
            LoadingScreen()
        }
        composable(RootRoutes.CONVERSION_WELCOME) {
            PoshanPatientTheme {
                ConversionWelcomeScreen(
                    onContinue = {
                        (authState as? AuthUiState.LoggedIn)?.let { navGraphViewModel.markConversionWelcomeSeen(it.userId) }
                        navController.navigate(RootRoutes.PATIENT_GRAPH) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
        authGraph(navController, authViewModel)
        patientGraph(navController)
        practitionerGraph(navController)
        adminGraph(navController)
        leadGraph(navController)
    }

    if (authState is AuthUiState.LoggedIn) {
        NotificationPermissionRequester()
    }
}

private fun resolveDeepLinkRoute(role: Role, target: DeepLinkTarget): String? = when (role) {
    Role.PATIENT -> when (target.relatedEntityType) {
        "report" -> target.relatedEntityId?.let { "patient/reports/$it" }
        else -> null
    }
    else -> null
}
