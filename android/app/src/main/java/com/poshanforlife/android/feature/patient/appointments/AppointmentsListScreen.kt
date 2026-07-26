package com.poshanforlife.android.feature.patient.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.AppointmentDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsListScreen(
    modifier: Modifier = Modifier,
    viewModel: AppointmentsViewModel = hiltViewModel(),
    onBookAppointment: () -> Unit = {},
    onRescheduleAppointment: (appointmentId: String, practitionerId: String) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    var appointmentToCancel by remember { mutableStateOf<AppointmentDto?>(null) }
    var appointmentToReschedule by remember { mutableStateOf<AppointmentDto?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onBookAppointment) {
                Icon(Icons.Filled.Add, contentDescription = "Book appointment")
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val state = uiState) {
                AppointmentsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is AppointmentsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load appointments: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                is AppointmentsUiState.Success -> {
                    val upcoming = state.appointments
                        .filter { it.status != "cancelled" }
                        .sortedBy { it.scheduledAt }

                    if (upcoming.isEmpty()) {
                        EmptyState()
                    } else {
                        val grouped = upcoming.groupBy { localDateOf(it.scheduledAt) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            grouped.forEach { (date, appointmentsOnDate) ->
                                item(key = "header-$date") {
                                    Text(
                                        text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                items(appointmentsOnDate, key = { it.id }) { appointment ->
                                    AppointmentCard(
                                        appointment = appointment,
                                        onReschedule = { appointmentToReschedule = appointment },
                                        onCancel = { appointmentToCancel = appointment },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    appointmentToReschedule?.let { appointment ->
        AlertDialog(
            onDismissRequest = { appointmentToReschedule = null },
            title = { Text("Reschedule appointment?") },
            text = { Text("Pick a new time for your appointment with ${appointment.practitioner.name}.") },
            confirmButton = {
                TextButton(onClick = {
                    onRescheduleAppointment(appointment.id, appointment.practitioner.id)
                    appointmentToReschedule = null
                }) { Text("Choose new time") }
            },
            dismissButton = {
                TextButton(onClick = { appointmentToReschedule = null }) { Text("Keep it") }
            },
        )
    }

    appointmentToCancel?.let { appointment ->
        AlertDialog(
            onDismissRequest = { appointmentToCancel = null },
            title = { Text("Cancel appointment?") },
            text = { Text("Your appointment with ${appointment.practitioner.name} will be cancelled.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancel(appointment.id)
                    appointmentToCancel = null
                }) { Text("Cancel appointment") }
            },
            dismissButton = {
                TextButton(onClick = { appointmentToCancel = null }) { Text("Keep it") }
            },
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::clearActionError) { Text("OK") }
            },
        )
    }
}

@Composable
private fun AppointmentCard(
    appointment: AppointmentDto,
    onReschedule: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = appointment.practitioner.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = timeOf(appointment.scheduledAt).format(DateTimeFormatter.ofPattern("h:mm a")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = appointment.status)
            }
            if (appointment.status == "scheduled") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onReschedule) { Text("Reschedule") }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
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
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No upcoming appointments",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Tap the + button to book a slot with your practitioner.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
            )
        }
    }
}

private fun localDateOf(scheduledAtIso: String) =
    Instant.parse(scheduledAtIso).atZone(ZoneId.systemDefault()).toLocalDate()

private fun timeOf(scheduledAtIso: String) =
    Instant.parse(scheduledAtIso).atZone(ZoneId.systemDefault()).toLocalTime()
