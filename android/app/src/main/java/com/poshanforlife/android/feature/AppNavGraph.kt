package com.poshanforlife.android.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.feature.admin.adminGraph
import com.poshanforlife.android.feature.auth.AuthUiState
import com.poshanforlife.android.feature.auth.AuthViewModel
import com.poshanforlife.android.feature.auth.authGraph
import com.poshanforlife.android.feature.lead.leadGraph
import com.poshanforlife.android.feature.patient.patientGraph
import com.poshanforlife.android.feature.practitioner.practitionerGraph
import com.poshanforlife.android.ui.components.LoadingScreen

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

    LaunchedEffect(authState) {
        val target = when (val state = authState) {
            AuthUiState.Loading -> null
            AuthUiState.LoggedOut -> RootRoutes.AUTH_GRAPH
            is AuthUiState.LoggedIn -> when (state.role) {
                Role.PATIENT -> RootRoutes.PATIENT_GRAPH
                Role.DOCTOR -> RootRoutes.PRACTITIONER_GRAPH
                Role.ADMIN -> RootRoutes.ADMIN_GRAPH
                Role.LEAD -> RootRoutes.LEAD_GRAPH
                // Unrecognized role (backend enum drift): fail safe to logged-out, never crash.
                Role.UNKNOWN -> RootRoutes.AUTH_GRAPH
            }
        }
        if (target != null && navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = RootRoutes.LOADING) {
        composable(RootRoutes.LOADING) {
            LoadingScreen()
        }
        authGraph(navController, authViewModel)
        patientGraph(navController)
        practitionerGraph(navController)
        adminGraph(navController)
        leadGraph(navController)
    }
}
