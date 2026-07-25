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
import com.poshanforlife.android.ui.components.BottomNavItem
import com.poshanforlife.android.ui.components.RoleScaffold

private const val ROOT = "lead/root"

/** "Health-only mode" self-signup accounts (Role.LEAD) — see backend prompt 11/mobile signup. */
private val items = listOf(
    BottomNavItem("lead/home", "Home", Icons.Filled.Home),
    BottomNavItem("lead/track", "Track", Icons.Filled.Timeline),
    BottomNavItem("lead/goals", "Goals", Icons.Filled.Flag),
    BottomNavItem("lead/profile", "Profile", Icons.Filled.Person),
)

fun NavGraphBuilder.leadGraph(navController: NavController) {
    navigation(startDestination = ROOT, route = RootRoutes.LEAD_GRAPH) {
        composable(ROOT) {
            RoleScaffold(items = items)
        }
    }
}
