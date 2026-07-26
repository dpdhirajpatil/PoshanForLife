package com.poshanforlife.android.feature.practitioner.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.AppointmentDto
import com.poshanforlife.android.ui.components.AppDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = hiltViewModel(),
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }
    var selectedAppointment by remember { mutableStateOf<AppointmentDto?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))) },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WeekStrip(selectedDate = selectedDate, onSelect = viewModel::selectDate)

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val state = uiState) {
                    ScheduleUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    is ScheduleUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            text = "Couldn't load schedule: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    is ScheduleUiState.Success -> {
                        if (state.appointments.isEmpty()) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text(
                                    text = "No appointments on this day",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(state.appointments, key = { it.id }) { appointment ->
                                    ScheduleCard(
                                        appointment = appointment,
                                        onClick = { selectedAppointment = appointment },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                viewModel.selectDate(it)
                showDatePicker = false
            },
        )
    }

    selectedAppointment?.let { appointment ->
        AppointmentDetailDialog(
            appointment = appointment,
            onDismiss = { selectedAppointment = null },
            onMarkCompleted = { notes ->
                viewModel.markCompleted(appointment.id, notes)
                selectedAppointment = null
            },
            onSaveNotes = { notes ->
                viewModel.saveNotes(appointment.id, notes)
                selectedAppointment = null
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearActionError) { Text("OK") } },
        )
    }
}

@Composable
private fun WeekStrip(selectedDate: LocalDate, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val days = (-3..3).map { today.plusDays(it.toLong()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            FilterChip(
                selected = day == selectedDate,
                onClick = { onSelect(day) },
                label = { Text(day.format(DateTimeFormatter.ofPattern("EEE d"))) },
            )
        }
    }
}

@Composable
private fun ScheduleCard(appointment: AppointmentDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (appointment.status == "completed") Icons.Filled.CheckCircle else Icons.Filled.Person,
                contentDescription = null,
                tint = if (appointment.status == "completed") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = appointment.patient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = timeOf(appointment.scheduledAt).format(DateTimeFormatter.ofPattern("h:mm a")) +
                        " · ${appointment.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(status = appointment.status)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (containerColor, contentColor) = when (status) {
        "scheduled" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "cancelled" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = containerColor) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AppointmentDetailDialog(
    appointment: AppointmentDto,
    onDismiss: () -> Unit,
    onMarkCompleted: (notes: String?) -> Unit,
    onSaveNotes: (notes: String) -> Unit,
) {
    var notes by remember(appointment.id) { mutableStateOf(appointment.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appointment.patient.name) },
        text = {
            Column {
                Text(
                    text = timeOf(appointment.scheduledAt).format(DateTimeFormatter.ofPattern("h:mm a")) +
                        " · ${appointment.durationMinutes} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Status: ${appointment.status.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            if (appointment.status == "scheduled") {
                TextButton(onClick = { onMarkCompleted(notes.ifBlank { null }) }) { Text("Mark completed") }
            } else {
                TextButton(onClick = { onSaveNotes(notes) }) { Text("Save notes") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun timeOf(scheduledAtIso: String) =
    Instant.parse(scheduledAtIso).atZone(ZoneId.systemDefault()).toLocalTime()
