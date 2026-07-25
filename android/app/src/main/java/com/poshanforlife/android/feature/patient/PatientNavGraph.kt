package com.poshanforlife.android.feature.patient

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.PlaceholderScreen

private const val HOME = "patient/home"

fun NavGraphBuilder.patientGraph(navController: NavController) {
    navigation(startDestination = HOME, route = RootRoutes.PATIENT_GRAPH) {
        composable(HOME) {
            PlaceholderScreen(label = "Patient home")
        }
    }
}
