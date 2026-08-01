package com.poshanforlife.android.feature.patient

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.appointments.AppointmentsListScreen
import com.poshanforlife.android.feature.patient.appointments.BookAppointmentScreen
import com.poshanforlife.android.feature.patient.appointments.PreCallScreen
import com.poshanforlife.android.feature.patient.appointments.VideoCallScreen
import com.poshanforlife.android.feature.patient.appointments.VideoCallViewModel
import com.poshanforlife.android.feature.patient.badges.BadgesScreen
import com.poshanforlife.android.feature.patient.profile.ProfileScreen
import com.poshanforlife.android.feature.patient.programmes.ProgrammeDetailScreen
import com.poshanforlife.android.feature.patient.programmes.ProgrammesListScreen
import com.poshanforlife.android.feature.patient.reports.ReportDetailScreen
import com.poshanforlife.android.feature.patient.reports.ReportsListScreen
import com.poshanforlife.android.feature.patient.track.GoalsScreen
import com.poshanforlife.android.feature.patient.track.TrackScreen
import com.poshanforlife.android.feature.products.ProductDetailScreen
import com.poshanforlife.android.feature.products.ProductsScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold
import com.poshanforlife.android.ui.theme.patientThemedComposable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private const val ROOT = "patient/root"
private const val GOALS_ROUTE = "patient/goals"
private const val REPORT_DETAIL_ROUTE = "patient/reports/{reportId}"
private const val PROGRAMME_DETAIL_ROUTE = "patient/programmes/{patientId}/{programmeId}"
private const val BOOK_APPOINTMENT_ROUTE = "patient/appointments/book"
private const val RESCHEDULE_APPOINTMENT_ROUTE = "patient/appointments/{appointmentId}/reschedule/{practitionerId}"

// AN-13 telemedicine scaffold: pre-call (real) -> call (placeholder until a video provider is chosen).
private const val PRE_CALL_ROUTE = "patient/appointments/{appointmentId}/pre-call"
private const val VIDEO_CALL_ROUTE = "patient/appointments/{appointmentId}/call"

private const val HOME_ROUTE = "patient/home"
private const val TRACK_ROUTE = "patient/track"
private const val PROGRAMMES_ROUTE = "patient/programmes"
private const val APPOINTMENTS_ROUTE = "patient/appointments"
private const val REPORTS_ROUTE = "patient/reports"
private const val BADGES_ROUTE = "patient/badges"
private const val PRODUCTS_ROUTE = "patient/products"
private const val PROFILE_ROUTE = "patient/profile"
private const val PRODUCT_DETAIL_ROUTE = "patient/products/{productId}"

private val items = listOf(
    BottomNavItem(HOME_ROUTE, "Home", Icons.Filled.Home),
    BottomNavItem(TRACK_ROUTE, "Track", Icons.Filled.Timeline),
    BottomNavItem(PROGRAMMES_ROUTE, "Programmes", Icons.AutoMirrored.Filled.MenuBook),
    BottomNavItem(APPOINTMENTS_ROUTE, "Appointments", Icons.Filled.CalendarMonth),
    BottomNavItem(REPORTS_ROUTE, "Reports", Icons.Filled.Description),
    BottomNavItem(BADGES_ROUTE, "Badges", Icons.Filled.EmojiEvents),
    BottomNavItem(PRODUCTS_ROUTE, "Products", Icons.Filled.ShoppingBag),
    BottomNavItem(PROFILE_ROUTE, "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PATIENT_GRAPH) {
        patientThemedComposable(ROOT) {
            val tabSwitchRequests = remember { MutableSharedFlow<String>() }
            val scope = rememberCoroutineScope()
            RoleScaffold(items = items, tabSwitchRequests = tabSwitchRequests) { route ->
                when (route) {
                    HOME_ROUTE -> DashboardScreen()
                    TRACK_ROUTE -> TrackScreen(
                        onConnectHealthConnect = { scope.launch { tabSwitchRequests.emit(PROFILE_ROUTE) } },
                        onOpenGoals = { navController.navigate(GOALS_ROUTE) },
                    )
                    PROGRAMMES_ROUTE -> ProgrammesListScreen(
                        onOpenProgramme = { patientId, programmeId ->
                            navController.navigate("patient/programmes/$patientId/$programmeId")
                        },
                    )
                    APPOINTMENTS_ROUTE -> AppointmentsListScreen(
                        onBookAppointment = { navController.navigate(BOOK_APPOINTMENT_ROUTE) },
                        onRescheduleAppointment = { appointmentId, practitionerId ->
                            navController.navigate("patient/appointments/$appointmentId/reschedule/$practitionerId")
                        },
                        onJoinCall = { appointmentId ->
                            navController.navigate("patient/appointments/$appointmentId/pre-call")
                        },
                    )
                    REPORTS_ROUTE -> ReportsListScreen(
                        onOpenReport = { reportId -> navController.navigate("patient/reports/$reportId") },
                    )
                    BADGES_ROUTE -> BadgesScreen()
                    // Read-only for every non-admin role — see AdminNavGraph for the admin-mode instance of this same screen.
                    PRODUCTS_ROUTE -> ProductsScreen(
                        isAdmin = false,
                        onOpenProduct = { productId -> navController.navigate("patient/products/$productId") },
                    )
                    PROFILE_ROUTE -> ProfileScreen()
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        // Opens as a full screen above the bottom-nav shell (settings-style, not a tab).
        patientThemedComposable(GOALS_ROUTE) {
            GoalsScreen()
        }
        patientThemedComposable(PRODUCT_DETAIL_ROUTE) {
            ProductDetailScreen(onBack = { navController.popBackStack() })
        }
        patientThemedComposable(REPORT_DETAIL_ROUTE) {
            ReportDetailScreen()
        }
        patientThemedComposable(PROGRAMME_DETAIL_ROUTE) {
            ProgrammeDetailScreen()
        }
        patientThemedComposable(BOOK_APPOINTMENT_ROUTE) {
            BookAppointmentScreen(onDone = { navController.popBackStack() })
        }
        patientThemedComposable(RESCHEDULE_APPOINTMENT_ROUTE) { backStackEntry ->
            BookAppointmentScreen(
                preselectedPractitionerId = backStackEntry.arguments?.getString("practitionerId"),
                rescheduleAppointmentId = backStackEntry.arguments?.getString("appointmentId"),
                onDone = { navController.popBackStack() },
            )
        }
        patientThemedComposable(PRE_CALL_ROUTE) { backStackEntry ->
            val appointmentId = backStackEntry.arguments?.getString("appointmentId").orEmpty()
            val viewModel: VideoCallViewModel = hiltViewModel()
            val appointment by viewModel.appointment.collectAsStateWithLifecycle()
            PreCallScreen(
                otherPartyName = appointment?.practitioner?.name.orEmpty(),
                onJoin = {
                    navController.navigate("patient/appointments/$appointmentId/call") {
                        // The pre-call screen has served its purpose once the call starts —
                        // leaving it on the stack would drop the user back into a camera
                        // preview when they end the call.
                        popUpTo(PRE_CALL_ROUTE) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        patientThemedComposable(VIDEO_CALL_ROUTE) {
            val viewModel: VideoCallViewModel = hiltViewModel()
            val appointment by viewModel.appointment.collectAsStateWithLifecycle()
            VideoCallScreen(
                otherPartyName = appointment?.practitioner?.name.orEmpty(),
                onLeave = { navController.popBackStack() },
            )
        }
    }
}
