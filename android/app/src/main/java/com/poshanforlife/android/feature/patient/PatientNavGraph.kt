package com.poshanforlife.android.feature.patient

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.feature.patient.programmes.ProgrammeDetailScreen
import com.poshanforlife.android.feature.patient.programmes.ProgrammesListScreen
import com.poshanforlife.android.feature.patient.reports.ReportDetailScreen
import com.poshanforlife.android.feature.patient.reports.ReportsListScreen
import com.poshanforlife.android.feature.patient.track.GoalsScreen
import com.poshanforlife.android.feature.patient.track.TrackScreen
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "patient/root"
private const val GOALS_ROUTE = "patient/goals"
private const val REPORT_DETAIL_ROUTE = "patient/reports/{reportId}"
private const val PROGRAMME_DETAIL_ROUTE = "patient/programmes/{patientId}/{programmeId}"

private const val HOME_ROUTE = "patient/home"
private const val TRACK_ROUTE = "patient/track"
private const val PROGRAMMES_ROUTE = "patient/programmes"
private const val REPORTS_ROUTE = "patient/reports"

private val items = listOf(
    BottomNavItem(HOME_ROUTE, "Home", Icons.Filled.Home),
    BottomNavItem(TRACK_ROUTE, "Track", Icons.Filled.Timeline),
    BottomNavItem(PROGRAMMES_ROUTE, "Programmes", Icons.AutoMirrored.Filled.MenuBook),
    BottomNavItem(REPORTS_ROUTE, "Reports", Icons.Filled.Description),
    BottomNavItem("patient/profile", "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PATIENT_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                when (route) {
                    HOME_ROUTE -> DashboardScreen()
                    TRACK_ROUTE -> TrackScreen(
                        onConnectHealthConnect = { /* TODO: wired up in AN-11 */ },
                        onOpenGoals = { navController.navigate(GOALS_ROUTE) },
                    )
                    PROGRAMMES_ROUTE -> ProgrammesListScreen(
                        onOpenProgramme = { patientId, programmeId ->
                            navController.navigate("patient/programmes/$patientId/$programmeId")
                        },
                    )
                    REPORTS_ROUTE -> ReportsListScreen(
                        onOpenReport = { reportId -> navController.navigate("patient/reports/$reportId") },
                    )
                    else -> PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
        // Opens as a full screen above the bottom-nav shell (settings-style, not a tab).
        composable(GOALS_ROUTE) {
            GoalsScreen()
        }
        composable(REPORT_DETAIL_ROUTE) {
            ReportDetailScreen()
        }
        composable(PROGRAMME_DETAIL_ROUTE) {
            ProgrammeDetailScreen()
        }
    }
}
