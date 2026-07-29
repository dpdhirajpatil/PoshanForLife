package com.poshanforlife.android.feature.practitioner.leads

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.LeadActivityDto
import com.poshanforlife.android.core.network.LeadDetailDto
import com.poshanforlife.android.ui.components.AppDatePickerDialog
import com.poshanforlife.android.ui.components.TimePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val STAGES = listOf("new", "contacted", "qualified", "proposed", "converted", "lost")

private val ACTIVITY_TYPES = listOf("note", "call", "whatsapp", "estimate_sent")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: LeadsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onConvert: () -> Unit = {},
) {
    val uiState by viewModel.detailState.collectAsStateWithLifecycle()
    val stageChangeState by viewModel.stageChangeState.collectAsStateWithLifecycle()
    val activityState by viewModel.activityState.collectAsStateWithLifecycle()
    val followupState by viewModel.followupState.collectAsStateWithLifecycle()

    var showLogActivity by remember { mutableStateOf(false) }
    var showFollowup by remember { mutableStateOf(false) }

    LaunchedEffect(activityState) {
        if (activityState is LeadActionState.Idle) showLogActivity = false
    }
    LaunchedEffect(followupState) {
        if (followupState is LeadActionState.Idle) showFollowup = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text((uiState as? LeadDetailUiState.Success)?.lead?.name ?: "Lead") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            LeadDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is LeadDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load this lead: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            is LeadDetailUiState.Success -> {
                val lead = state.lead
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { ContactInfoCard(lead = lead) }

                    item {
                        StageSection(
                            lead = lead,
                            changing = stageChangeState is LeadActionState.InFlight,
                            onStageChange = viewModel::changeStage,
                        )
                    }

                    if (lead.stage != "converted") {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { showFollowup = true }, modifier = Modifier.weight(1f)) {
                                    Text("Schedule follow-up")
                                }
                                Button(onClick = onConvert, modifier = Modifier.weight(1f)) {
                                    Text("Convert to patient")
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Activity timeline", style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { showLogActivity = true }) { Text("Log activity") }
                        }
                    }

                    if (lead.activities.isEmpty()) {
                        item {
                            Text(
                                text = "No activity logged yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(lead.activities, key = { it.id }) { activity ->
                            ActivityRow(activity = activity)
                        }
                    }
                }

                if (showLogActivity) {
                    LogActivitySheet(
                        submitting = activityState is LeadActionState.InFlight,
                        error = (activityState as? LeadActionState.Error)?.message,
                        onDismiss = { showLogActivity = false },
                        onSubmit = { type, description -> viewModel.logActivity(type, description) },
                    )
                }

                if (showFollowup) {
                    ScheduleFollowupDialog(
                        submitting = followupState is LeadActionState.InFlight,
                        error = (followupState as? LeadActionState.Error)?.message,
                        onDismiss = { showFollowup = false },
                        onConfirm = { followupAt, message -> viewModel.scheduleFollowup(followupAt, message) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactInfoCard(lead: LeadDetailDto) {
    DetailCard {
        Text(text = "Contact info", style = MaterialTheme.typography.titleLarge)
        DetailRow("Phone", lead.phone ?: "—")
        DetailRow("Email", lead.email ?: "—")
        DetailRow("City", lead.city ?: "—")
        lead.age?.let { DetailRow("Age", it.toString()) }
        lead.interestedProgramme?.let { DetailRow("Interested in", it.name) }
        if (!lead.healthGoal.isNullOrBlank()) DetailRow("Health goal", lead.healthGoal)
        if (!lead.notes.isNullOrBlank()) DetailRow("Notes", lead.notes)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageSection(lead: LeadDetailDto, changing: Boolean, onStageChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    DetailCard {
        Text(text = "Stage", style = MaterialTheme.typography.titleLarge)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it && lead.stage != "converted" },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = lead.stage.replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                enabled = lead.stage != "converted" && !changing,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                STAGES.forEach { stage ->
                    DropdownMenuItem(
                        text = { Text(stage.replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            expanded = false
                            if (stage != lead.stage) onStageChange(stage)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: LeadActivityDto) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .background(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
        ) {
            Icon(
                imageVector = iconForActivityType(activity.activityType),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = activity.description ?: activity.activityType.replace('_', ' ').replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyLarge,
            )
            Row {
                activity.createdBy?.let {
                    Text(
                        text = "${it.name} · ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = activityDateLabel(activity.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun iconForActivityType(activityType: String): ImageVector = when (activityType) {
    "call" -> Icons.Filled.Call
    "whatsapp" -> Icons.AutoMirrored.Filled.Chat
    "estimate_sent" -> Icons.Filled.Receipt
    "stage_change" -> Icons.Filled.SwapHoriz
    "converted" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.EditNote
}

private fun activityDateLabel(createdAt: String?): String {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogActivitySheet(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (activityType: String, description: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedType by remember { mutableStateOf(ACTIVITY_TYPES.first()) }
    var description by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Log activity", style = MaterialTheme.typography.titleLarge)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                ACTIVITY_TYPES.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ACTIVITY_TYPES.size),
                    ) { Text(type.replace('_', ' ').replaceFirstChar { it.uppercase() }) }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                minLines = 3,
            )

            if (error != null) {
                Text(
                    text = "Couldn't log activity: $error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Button(
                onClick = { onSubmit(selectedType, description) },
                enabled = description.isNotBlank() && !submitting,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
            ) {
                Text(if (submitting) "Saving…" else "Save activity")
            }
        }
    }
}

@Composable
private fun ScheduleFollowupDialog(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (followupAt: String, message: String?) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(10, 0)) }
    var message by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule follow-up") },
        text = {
            Column {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Event, contentDescription = null)
                    Text(text = " ${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(text = time.toString())
                }
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message (optional)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 2,
                )
                if (error != null) {
                    Text(
                        text = "Couldn't schedule: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val followupAt = java.time.ZonedDateTime.of(date, time, ZoneId.systemDefault()).toOffsetDateTime().toString()
                    onConfirm(followupAt, message.ifBlank { null })
                },
            ) { Text(if (submitting) "Saving…" else "Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        AppDatePickerDialog(initial = date, onDismiss = { showDatePicker = false }, onConfirm = { date = it; showDatePicker = false })
    }
    if (showTimePicker) {
        TimePickerDialog(initial = time, onDismiss = { showTimePicker = false }, onConfirm = { time = it; showTimePicker = false })
    }
}

@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
