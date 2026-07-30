package com.poshanforlife.android.feature.lead

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.track.GoalsScreen
import com.poshanforlife.android.feature.patient.track.TrackScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "lead/root"
private const val REQUEST_CONSULTATION_ROUTE = "lead/request-consultation"

private const val HOME_ROUTE = "lead/home"
private const val TRACK_ROUTE = "lead/track"
private const val GOALS_TAB_ROUTE = "lead/goals-tab"
private const val PROFILE_ROUTE = "lead/profile"

/** Full-screen sibling route (not a tab) — reached from the Track tab's own "Goals" button, same as the patient graph's shape. */
private const val GOALS_DETAIL_ROUTE = "lead/goals"

/** "Health-only mode" self-signup accounts (Role.LEAD) — see backend prompt 11/mobile signup. */
private val items = listOf(
    BottomNavItem(HOME_ROUTE, "Home", Icons.Filled.Home),
    BottomNavItem(TRACK_ROUTE, "Track", Icons.Filled.Timeline),
    BottomNavItem(GOALS_TAB_ROUTE, "Goals", Icons.Filled.Flag),
    BottomNavItem(PROFILE_ROUTE, "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.leadGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.LEAD_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    HOME_ROUTE -> LeadHomeScreen(
                        onRequestConsultation = { navController.navigate(REQUEST_CONSULTATION_ROUTE) },
                    )
                    // Reuses AN-04's tracking screens verbatim — same cross-graph reuse
                    // convention as AN-09's detail-screen sharing; LEAD's health-record
                    // writes are explicitly authorized server-side (@AdminOrDoctorOrPatientOrLead).
                    TRACK_ROUTE -> TrackScreen(onOpenGoals = { navController.navigate(GOALS_DETAIL_ROUTE) })
                    GOALS_TAB_ROUTE -> GoalsScreen()
                    PROFILE_ROUTE -> LeadProfileScreen()
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        composable(GOALS_DETAIL_ROUTE) {
            GoalsScreen()
        }
        composable(REQUEST_CONSULTATION_ROUTE) {
            RequestConsultationScreen(onDone = { navController.popBackStack() })
        }
    }
}
