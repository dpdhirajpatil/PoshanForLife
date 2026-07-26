package com.poshanforlife.android.feature.patient.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.poshanforlife.android.core.network.AvailableSlotDto
import com.poshanforlife.android.ui.components.AppDatePickerDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * practitionerId preselected when rescheduling an existing appointment
 * (rescheduleAppointmentId non-null); otherwise the user first picks from
 * their own assigned practitioners.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookAppointmentScreen(
    modifier: Modifier = Modifier,
    viewModel: AppointmentsViewModel = hiltViewModel(),
    preselectedPractitionerId: String? = null,
    rescheduleAppointmentId: String? = null,
    onDone: () -> Unit = {},
) {
    val practitioners by viewModel.practitioners.collectAsState()
    val slotsUiState by viewModel.slotsUiState.collectAsState()
    val bookingState by viewModel.bookingState.collectAsState()

    var selectedPractitionerId by remember { mutableStateOf(preselectedPractitionerId) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTime by remember { mutableStateOf<LocalTime?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (preselectedPractitionerId == null) viewModel.loadPractitioners()
    }
    LaunchedEffect(selectedPractitionerId, selectedDate) {
        val practitionerId = selectedPractitionerId
        val date = selectedDate
        if (practitionerId != null && date != null) {
            selectedTime = null
            viewModel.loadSlots(practitionerId, date)
        }
    }
    LaunchedEffect(bookingState) {
        if (bookingState is BookingState.Success) {
            viewModel.resetBookingState()
            onDone()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(if (rescheduleAppointmentId != null) "Reschedule appointment" else "Book appointment") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (preselectedPractitionerId == null) {
                Text(text = "Practitioner", style = MaterialTheme.typography.titleMedium)
                if (practitioners.isEmpty()) {
                    Text(
                        text = "No assigned practitioner found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        practitioners.forEach { practitioner ->
                            FilterChip(
                                selected = selectedPractitionerId == practitioner.id,
                                onClick = { selectedPractitionerId = practitioner.id },
                                label = { Text(practitioner.name) },
                            )
                        }
                    }
                }
            }

            Text(text = "Date", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(
                    selectedDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        ?: "Choose a date",
                )
            }

            if (selectedPractitionerId != null && selectedDate != null) {
                Text(text = "Available times", style = MaterialTheme.typography.titleMedium)
                when (val state = slotsUiState) {
                    SlotsUiState.Idle -> Unit
                    SlotsUiState.Loading -> CircularProgressIndicator()
                    is SlotsUiState.Error -> Text(
                        text = "Couldn't load slots: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                    )
                    is SlotsUiState.Success -> SlotGrid(
                        slots = state.slots,
                        selectedTime = selectedTime,
                        onSelect = { selectedTime = it },
                    )
                }
            }

            Spacer(modifier = Modifier.height(0.dp))

            if (bookingState is BookingState.Error) {
                Text(
                    text = (bookingState as BookingState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    val practitionerId = selectedPractitionerId
                    val date = selectedDate
                    val time = selectedTime
                    if (practitionerId != null && date != null && time != null) {
                        val scheduledAtIso = date.atTime(time).atZone(java.time.ZoneOffset.UTC).toInstant().toString()
                        viewModel.confirmBooking(practitionerId, scheduledAtIso, rescheduleAppointmentId)
                    }
                },
                enabled = selectedPractitionerId != null && selectedDate != null && selectedTime != null &&
                    bookingState !is BookingState.InProgress,
                modifier = Modifier.wrapContentWidth(),
            ) {
                if (bookingState is BookingState.InProgress) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                } else {
                    Text(if (rescheduleAppointmentId != null) "Confirm new time" else "Confirm booking")
                }
            }
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initial = selectedDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onConfirm = {
                selectedDate = it
                showDatePicker = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotGrid(slots: List<AvailableSlotDto>, selectedTime: LocalTime?, onSelect: (LocalTime) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { slot ->
            val time = LocalTime.parse(slot.time)
            FilterChip(
                selected = selectedTime == time,
                onClick = { if (slot.available) onSelect(time) },
                enabled = slot.available,
                label = { Text(time.format(DateTimeFormatter.ofPattern("h:mm a"))) },
            )
        }
    }
}
