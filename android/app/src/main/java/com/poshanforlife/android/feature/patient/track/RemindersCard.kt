package com.poshanforlife.android.feature.patient.track

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.data.local.MedicationReminderEntity
import com.poshanforlife.android.core.reminder.nextOccurrenceLabel
import com.poshanforlife.android.ui.components.TimePickerDialog
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun RemindersCard(viewModel: ReminderViewModel, modifier: Modifier = Modifier) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    fun currentlyGranted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    // Plain checkSelfPermission() reads aren't observable by Compose — this state is what
    // actually triggers recomposition once the user responds to the system dialog.
    var hasNotificationPermission by remember { mutableStateOf(currentlyGranted()) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasNotificationPermission = granted
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Medication reminders", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(12.dp))

            if (!hasNotificationPermission) {
                Text(
                    text = "Allow notifications to receive reminders.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text("Enable notifications")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (reminders.isEmpty()) {
                Text(
                    text = "No reminders set",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                reminders.forEach { reminder ->
                    ReminderRow(
                        reminder = reminder,
                        onToggle = { viewModel.setEnabled(reminder, it) },
                        onDelete = { viewModel.delete(reminder) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { showAddDialog = true }) { Text("Add reminder") }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, time, days ->
                viewModel.add(label, time, days)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ReminderRow(reminder: MedicationReminderEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = reminder.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = nextOccurrenceLabel(reminder.timeOfDay, reminder.daysOfWeek),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = reminder.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete reminder")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onConfirm: (label: String, timeOfDay: String, daysOfWeek: String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var showTimePicker by remember { mutableStateOf(false) }
    val selectedDays = remember { mutableStateOf(setOf<DayOfWeek>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text("Time: %02d:%02d".format(time.hour, time.minute))
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day in selectedDays.value
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays.value = if (selected) selectedDays.value - day else selectedDays.value + day
                            },
                            label = { Text(day.name.take(3)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val timeOfDay = "%02d:%02d".format(time.hour, time.minute)
                    val daysOfWeek = selectedDays.value.joinToString(",") { it.name }
                    if (label.isNotBlank() && selectedDays.value.isNotEmpty()) {
                        onConfirm(label.trim(), timeOfDay, daysOfWeek)
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showTimePicker) {
        TimePickerDialog(initial = time, onDismiss = { showTimePicker = false }) {
            time = it
            showTimePicker = false
        }
    }
}
