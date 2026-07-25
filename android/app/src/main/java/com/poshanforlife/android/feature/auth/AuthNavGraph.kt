package com.poshanforlife.android.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes
import com.poshanforlife.android.ui.components.PlaceholderScreen

private const val LOGIN = "auth/login"

/** Login/signup — real screens land in AN-02 alongside AuthViewModel. */
fun NavGraphBuilder.authGraph(navController: NavController) {
    navigation(startDestination = LOGIN, route = RootRoutes.AUTH_GRAPH) {
        composable(LOGIN) {
            PlaceholderScreen(label = "Auth")
        }
    }
}
