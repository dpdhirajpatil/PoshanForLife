package com.poshanforlife.android.feature.practitioner

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.PlaceholderScreen

private const val HOME = "practitioner/home"

fun NavGraphBuilder.practitionerGraph(navController: NavController) {
    navigation(startDestination = HOME, route = RootRoutes.PRACTITIONER_GRAPH) {
        composable(HOME) {
            PlaceholderScreen(label = "Practitioner home")
        }
    }
}
