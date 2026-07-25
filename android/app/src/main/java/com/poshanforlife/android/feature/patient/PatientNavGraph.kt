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
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.PlaceholderScreen
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "patient/root"

private const val HOME_ROUTE = "patient/home"

private val items = listOf(
    BottomNavItem(HOME_ROUTE, "Home", Icons.Filled.Home),
    BottomNavItem("patient/track", "Track", Icons.Filled.Timeline),
    BottomNavItem("patient/programmes", "Programmes", Icons.AutoMirrored.Filled.MenuBook),
    BottomNavItem("patient/reports", "Reports", Icons.Filled.Description),
    BottomNavItem("patient/profile", "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.PATIENT_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items) { route ->
                if (route == HOME_ROUTE) {
                    DashboardScreen()
                } else {
                    PlaceholderScreen(label = items.first { it.route == route }.label)
                }
            }
        }
    }
}
