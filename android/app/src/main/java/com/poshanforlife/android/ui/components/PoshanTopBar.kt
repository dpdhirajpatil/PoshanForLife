package com.poshanforlife.android.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.poshanforlife.android.R
import com.poshanforlife.android.feature.notifications.NotificationBell

/** The one shared top app bar behind all 4 role graphs (via RoleScaffold) — bell icon lives here so every role gets it for free. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoshanTopBar() {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = { NotificationBell() },
    )
}
