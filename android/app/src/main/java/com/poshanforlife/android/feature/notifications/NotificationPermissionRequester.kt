package com.poshanforlife.android.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shown once (ever, per install — persisted in TokenDataStore, survives
 * logout) the first time a role graph renders after login, i.e. the first
 * app open post-login, per AN-08's spec. Skipped entirely on API < 33, where
 * POST_NOTIFICATIONS is granted automatically at install time.
 */
@Composable
fun NotificationPermissionRequester(viewModel: NotificationPermissionViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val alreadyAsked by viewModel.alreadyAsked.collectAsStateWithLifecycle()
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.markAsked()
    }

    LaunchedEffect(alreadyAsked) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!alreadyAsked && !granted) {
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                viewModel.markAsked()
            },
            title = { Text("Stay in the loop") },
            text = { Text("Get notified when your practitioner assigns a new report or programme.") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    viewModel.markAsked()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    viewModel.markAsked()
                }) { Text("Not now") }
            },
        )
    }
}
