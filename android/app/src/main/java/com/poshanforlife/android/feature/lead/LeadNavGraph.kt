package com.poshanforlife.android.feature.lead

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.PlaceholderScreen

private const val HOME = "lead/home"

/** "Health-only mode" self-signup accounts (Role.LEAD) — see backend prompt 11/mobile signup. */
fun NavGraphBuilder.leadGraph(navController: NavController) {
    navigation(startDestination = HOME, route = RootRoutes.LEAD_GRAPH) {
        composable(HOME) {
            PlaceholderScreen(label = "Lead home")
        }
    }
}
