package com.poshanforlife.android.feature.patient.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.feature.auth.LinkPhoneCard
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfileScreen(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(viewModel.permissionRequestContract()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    // Health Connect's own settings screen (used for both "grant more" and "disconnect") isn't
    // guaranteed to return a permission-shaped result, so any return from it just re-checks state.
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onReturnedFromHealthConnectSettings()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Profile", style = MaterialTheme.typography.titleLarge)
        state.userName?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HealthConnectCard(
            state = state,
            onConnect = { permissionLauncher.launch(viewModel.requiredPermissions) },
            onInstall = { settingsLauncher.launch(viewModel.playStoreIntent()) },
            onSyncNow = viewModel::syncNow,
            onDisconnect = { settingsLauncher.launch(viewModel.healthConnectSettingsIntent()) },
        )

        // AN-23: lets an email-signup account add OTP sign-in. Re-reads the
        // profile on success so the card flips to the confirmed state.
        LinkPhoneCard(
            verifiedPhone = state.verifiedPhone,
            onLinked = viewModel::refreshVerifiedPhone,
        )

        TextButton(onClick = viewModel::logout) { Text("Log out") }
    }
}

@Composable
private fun HealthConnectCard(
    state: ProfileUiState,
    onConnect: () -> Unit,
    onInstall: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Health Connect", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Sync steps, sleep, heart rate, and weight automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val statusText = when {
                !state.isHealthConnectAvailable -> "Health Connect isn't installed"
                state.isHealthConnectConnected -> "Connected"
                else -> "Not connected"
            }
            Text(text = statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)

            if (state.isHealthConnectConnected) {
                state.lastSyncedAtMillis?.let {
                    Text(
                        text = "Last synced ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onSyncNow) { Text("Sync now") }
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else if (state.isHealthConnectAvailable) {
                Button(onClick = onConnect) { Text("Connect Health Connect") }
            } else {
                Button(onClick = onInstall) { Text("Install Health Connect") }
            }
        }
    }
}
