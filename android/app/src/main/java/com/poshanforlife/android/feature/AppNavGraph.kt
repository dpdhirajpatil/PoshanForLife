package com.poshanforlife.android.feature

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.poshanforlife.android.feature.admin.adminGraph
import com.poshanforlife.android.feature.auth.authGraph
import com.poshanforlife.android.feature.lead.leadGraph
import com.poshanforlife.android.feature.patient.patientGraph
import com.poshanforlife.android.feature.practitioner.practitionerGraph
import com.poshanforlife.android.ui.components.LoadingScreen

/**
 * Root NavHost. Currently only shows Loading — AN-02 replaces LOADING with a
 * decision based on AuthViewModel's role state (auth graph if signed out,
 * otherwise the graph matching the signed-in user's role) and calls
 * navController.navigate(roleGraphRoute) { popUpTo(RootRoutes.LOADING) {
 * inclusive = true } } once that state resolves. The four role graphs are
 * wired in now so that hookup is the only change AN-02 needs to make here.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = RootRoutes.LOADING) {
        composable(RootRoutes.LOADING) {
            LoadingScreen()
        }
        authGraph(navController)
        patientGraph(navController)
        practitionerGraph(navController)
        adminGraph(navController)
        leadGraph(navController)
    }
}
