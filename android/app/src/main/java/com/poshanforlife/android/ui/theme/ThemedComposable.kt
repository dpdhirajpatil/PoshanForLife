package com.poshanforlife.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * MainActivity's root wrap only covers Loading/Auth/Practitioner/Admin (all PoshanStaffTheme) —
 * Navigation Compose swaps out content per destination rather than nesting descendants under a
 * parent composable, so Patient/Lead graphs must re-apply their own theme at every composable()
 * in their nav graph, not just the tab-root one. Use these instead of the raw `composable(...)`
 * builder for any destination inside patientGraph/leadGraph.
 */
fun NavGraphBuilder.patientThemedComposable(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { backStackEntry -> PoshanPatientTheme { content(backStackEntry) } }
}

fun NavGraphBuilder.leadThemedComposable(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { backStackEntry -> PoshanLeadTheme { content(backStackEntry) } }
}
