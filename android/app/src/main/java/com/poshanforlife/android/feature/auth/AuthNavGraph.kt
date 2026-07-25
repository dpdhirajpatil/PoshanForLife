package com.poshanforlife.android.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.poshanforlife.android.feature.RootRoutes

private const val LOGIN = "auth/login"

/**
 * authViewModel is the single shared instance created once in AppNavGraph
 * (scoped to the Activity, not this destination) — LoginScreen calls
 * login() on it, and AppNavGraph observes the same instance's uiState to
 * decide when to swap to a role graph.
 */
fun NavGraphBuilder.authGraph(navController: NavController, authViewModel: AuthViewModel) {
    navigation(startDestination = LOGIN, route = RootRoutes.AUTH_GRAPH) {
        composable(LOGIN) {
            LoginScreen(authViewModel = authViewModel)
        }
    }
}
