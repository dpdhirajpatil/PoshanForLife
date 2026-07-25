package com.poshanforlife.android.feature.admin

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.PlaceholderScreen

private const val HOME = "admin/home"

fun NavGraphBuilder.adminGraph(navController: NavController) {
    navigation(startDestination = HOME, route = RootRoutes.ADMIN_GRAPH) {
        composable(HOME) {
            PlaceholderScreen(label = "Admin home")
        }
    }
}
