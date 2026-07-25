package com.poshanforlife.android.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Shared shell for all 4 role graphs: a Material 3 NavigationBar wrapping its
 * own internal NavHost of tabs, so each role graph is a single destination in
 * the outer AppNavGraph (see feature/{patient,practitioner,admin,lead}) while
 * still getting normal back-stack behavior (save/restoreState) between tabs.
 */
@Composable
fun RoleScaffold(items: List<BottomNavItem>, modifier: Modifier = Modifier) {
    val tabNavController = rememberNavController()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                val backStackEntry by tabNavController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                items.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            tabNavController.navigate(item.route) {
                                popUpTo(tabNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = items.first().route,
            modifier = Modifier.padding(padding),
        ) {
            items.forEach { item -> tab(item.route) { PlaceholderScreen(label = item.label) } }
        }
    }
}

private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) {
    composable(route) { content() }
}
