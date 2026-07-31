package com.poshanforlife.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.poshanforlife.android.R
import com.poshanforlife.android.feature.notifications.NotificationBell

/**
 * The one shared top app bar behind all 4 role graphs (via RoleScaffold/RoleScaffoldDrawer) —
 * bell icon lives here so every role gets it for free. [onMenuClick] is only passed by
 * RoleScaffoldDrawer (Admin) to show a hamburger icon opening its ModalNavigationDrawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoshanTopBar(onMenuClick: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        navigationIcon = {
            if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                }
            }
        },
        actions = { NotificationBell() },
    )
}
