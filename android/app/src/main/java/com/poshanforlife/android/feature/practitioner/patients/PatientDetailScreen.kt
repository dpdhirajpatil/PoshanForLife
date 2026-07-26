package com.poshanforlife.android.feature.practitioner.patients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientDetailDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.ReportListItemDto
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val TABS = listOf("Overview", "Reports", "Programmes", "Notes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PatientManagementViewModel = hiltViewModel(),
    onOpenReport: (reportId: String) -> Unit = {},
    onOpenProgramme: (programmeId: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text((detailState as? PatientDetailUiState.Success)?.patient?.name ?: "Patient")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = detailState) {
            PatientDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is PatientDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load patient: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            is PatientDetailUiState.Success -> {
                var selectedTab by remember { mutableIntStateOf(0) }
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    ProfileHeader(state.patient)
                    TabRow(selectedTabIndex = selectedTab) {
                        TABS.forEachIndexed { index, label ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(label) },
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> OverviewTab(state.patient)
                        1 -> ReportsTab(viewModel, onOpenReport)
                        2 -> ProgrammesTab(viewModel, onOpenProgramme)
                        3 -> NotesTab(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(patient: PatientDetailDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val initials = patient.name.trim().split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
            Column {
                Text(text = patient.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = ageAndGenderLabel(patient),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        patient.email?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        patient.phone?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun ageAndGenderLabel(patient: PatientDetailDto): String {
    val age = patient.dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { Period.between(it, LocalDate.now()).years }
    val parts = listOfNotNull(age?.let { "$it yrs" }, patient.gender, patient.bloodGroup)
    return if (parts.isEmpty()) "No profile details yet" else parts.joinToString(" · ")
}

@Composable
private fun OverviewTab(patient: PatientDetailDto) {
    val latest = patient.healthRecords.firstOrNull()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = "Latest vitals", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        if (latest == null) {
            Text(
                text = "No health records yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            VitalsRow(latest)
        }
        patient.medicalHistory?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "Medical history", style = MaterialTheme.typography.titleMedium)
            Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        patient.emergencyContact?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Emergency contact", style = MaterialTheme.typography.titleMedium)
            Text(text = it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun VitalsRow(record: HealthRecordDto) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatColumn("Weight", record.weightKg?.let { "%.1f kg".format(Locale.US, it) } ?: "—")
        StatColumn("BMI", record.bmi?.let { "%.1f".format(Locale.US, it) } ?: "—")
        StatColumn("Body fat", record.bodyFatPct?.let { "%.1f%%".format(Locale.US, it) } ?: "—")
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportsTab(viewModel: PatientManagementViewModel, onOpenReport: (String) -> Unit) {
    val state by viewModel.reportsState.collectAsStateWithLifecycle()
    when (val s = state) {
        PatientReportsUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is PatientReportsUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Couldn't load reports: ${s.message}", modifier = Modifier.padding(24.dp))
        }
        is PatientReportsUiState.Success -> {
            if (s.reports.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No reports yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.reports, key = { it.id }) { report ->
                        ReportRow(report, onClick = { onOpenReport(report.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: ReportListItemDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(text = report.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${report.type.replaceFirstChar { it.uppercase() }} · ${dateOf(report.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(status = report.status)
        }
    }
}

@Composable
private fun ProgrammesTab(viewModel: PatientManagementViewModel, onOpenProgramme: (String) -> Unit) {
    val state by viewModel.programmesState.collectAsStateWithLifecycle()
    when (val s = state) {
        PatientProgrammesUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is PatientProgrammesUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Couldn't load programmes: ${s.message}", modifier = Modifier.padding(24.dp))
        }
        is PatientProgrammesUiState.Success -> {
            if (s.programmes.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("No programmes assigned yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.programmes, key = { it.id }) { programme ->
                        ProgrammeRow(programme, onClick = { onOpenProgramme(programme.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(programme: PatientProgrammeDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = iconFor(programme.serviceType), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = programme.catalogueItem?.name ?: "Service",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                programme.startDate?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusBadge(status = programme.status)
        }
    }
}

private fun iconFor(serviceType: String?): ImageVector = when (serviceType) {
    "programme" -> Icons.AutoMirrored.Filled.MenuBook
    "session" -> Icons.Filled.Event
    "challenge" -> Icons.Filled.EmojiEvents
    else -> Icons.Filled.HelpOutline
}

@Composable
private fun StatusBadge(status: String) {
    val (containerColor, contentColor) = when (status) {
        "active", "scheduled" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "cancelled", "error" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
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
private fun NotesTab(viewModel: PatientManagementViewModel) {
    val notes by viewModel.notesText.collectAsStateWithLifecycle()
    val saveState by viewModel.notesSaveState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = "Practitioner notes", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Only visible to you and other practitioners — the patient never sees this.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = viewModel::onNotesChange,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            minLines = 8,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::saveNotes, enabled = saveState != NotesSaveState.Saving) {
                Text(if (saveState == NotesSaveState.Saving) "Saving…" else "Save")
            }
            when (val s = saveState) {
                NotesSaveState.Saved -> Text(
                    text = "Saved",
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                is NotesSaveState.Error -> Text(
                    text = "Couldn't save: ${s.message}",
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }
        }
    }
}

private fun dateOf(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
