package com.poshanforlife.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.ui.components.AppearanceCard

/**
 * Standalone Appearance setting for Practitioner (a "More" menu destination) and
 * Admin (a drawer destination). No Scaffold/TopAppBar of its own — Practitioner's
 * full-screen sibling routes rely on the system back gesture, and Admin renders
 * this inside RoleScaffoldDrawer, which already owns the top bar.
 *
 * Patient and Lead don't use this screen; they embed [AppearanceCard] directly in
 * their existing Profile screens rather than gaining a route for one setting.
 */
@Composable
fun AppearanceScreen(
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        AppearanceCard(
            selected = themeMode,
            onSelect = viewModel::setThemeMode,
        )
    }
}
