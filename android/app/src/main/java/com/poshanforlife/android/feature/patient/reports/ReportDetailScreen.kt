package com.poshanforlife.android.feature.patient.reports

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.domain.model.Role
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.InBodyDataDto
import com.poshanforlife.android.ui.components.HealthTrendChart
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val trends by viewModel.trends.collectAsStateWithLifecycle()
    val trendWindow by viewModel.trendWindow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val canEdit = currentUserRole == Role.DOCTOR || currentUserRole == Role.ADMIN
    val canDelete = currentUserRole == Role.ADMIN

    LaunchedEffect(deleteState) {
        if (deleteState is ReportDeleteState.Deleted) onBack()
        if (deleteState is ReportDeleteState.Error) {
            scope.launch { snackbarHostState.showSnackbar((deleteState as ReportDeleteState.Error).message) }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete report?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit report")
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete report")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            ReportDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ReportDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load this report: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            is ReportDetailUiState.Success -> {
                val report = state.report
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Column {
                            // ALL CAPS per Type.kt's headline contract (Patient theme).
                            Text(text = report.title.uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = report.patient?.name ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (report.confidence != null && report.confidence.lowercase() == "low") {
                        item { LowConfidenceBadge() }
                    }

                    val data = report.parsedData
                    if (data != null) {
                        inBodyGroups(data).forEach { group ->
                            item { FieldGroupCard(group) }
                        }
                    } else if (report.type != "inbody") {
                        report.notes?.takeIf { it.isNotBlank() }?.let {
                            item {
                                Column {
                                    Text(text = "Notes", style = MaterialTheme.typography.titleMedium)
                                    Text(text = it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "No parsed data available for this report.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (report.fileUrl != null) {
                        item {
                            Button(
                                onClick = {
                                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(report.fileUrl))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("View original PDF") }
                        }
                    }

                    // Trends are patient-wide, not specific to this report — only meaningful
                    // for body-composition reports, so they're hidden for lab/prescription/other.
                    if (report.type == "inbody") {
                        item {
                            TrendsSection(
                                state = trends,
                                selectedWindow = trendWindow,
                                onWindowChange = viewModel::onTrendWindowChange,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * AN-05's trend charts: weight, body fat, BMI and skeletal muscle mass over the
 * selected window, each fed by the same `GET /health-records` response.
 */
@Composable
private fun TrendsSection(
    state: TrendsUiState,
    selectedWindow: TrendWindow,
    onWindowChange: (TrendWindow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Trends", style = MaterialTheme.typography.titleLarge)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TrendWindow.entries.forEachIndexed { index, window ->
                SegmentedButton(
                    selected = window == selectedWindow,
                    onClick = { onWindowChange(window) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TrendWindow.entries.size),
                ) { Text(window.label) }
            }
        }

        when (state) {
            TrendsUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is TrendsUiState.Error -> Text(
                text = "Couldn't load trends: ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is TrendsUiState.Success -> {
                val records = state.records
                if (records.isEmpty()) {
                    Text(
                        text = "No health records yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TREND_METRICS.forEach { metric ->
                        HealthTrendChart(
                            label = metric.label,
                            unit = metric.unit,
                            values = records.mapNotNull(metric.value),
                            latestDelta = records.lastNotNullOf(metric.delta),
                        )
                    }
                }
            }
        }
    }
}

/** The four metrics AN-05 charts, paired with the server-computed delta for each. */
private data class TrendMetric(
    val label: String,
    val unit: String,
    val value: (HealthRecordDto) -> Double?,
    val delta: (HealthRecordDto) -> Double?,
)

private val TREND_METRICS = listOf(
    TrendMetric("Weight", "kg", { it.weightKg }, { it.weightKgDelta }),
    TrendMetric("Body fat", "%", { it.bodyFatPct }, { it.bodyFatPctDelta }),
    TrendMetric("BMI", "", { it.bmi }, { it.bmiDelta }),
    TrendMetric("Skeletal muscle mass", "kg", { it.skeletalMuscleMassKg }, { it.skeletalMuscleMassKgDelta }),
)

/**
 * The delta belonging to the most recent record that actually has one — the newest
 * record can legitimately be missing a metric (a manual weight-only log between two
 * InBody scans), in which case the last real reading's delta is the honest thing to show.
 */
private fun <T> List<T>.lastNotNullOf(selector: (T) -> Double?): Double? =
    asReversed().firstNotNullOfOrNull(selector)

@Composable
private fun LowConfidenceBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = "Low-confidence extraction — verify against the original PDF",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private data class FieldRow(val label: String, val value: String)
private data class FieldGroup(val title: String, val rows: List<FieldRow>)

/**
 * Backend's parsedData is a flat 20-field record with no server-side grouping
 * (see InBodyData.java) — these 3 groups mirror the web frontend's
 * inbody-field-groups.ts client-side grouping. There is no Segmental Lean
 * Analysis data anywhere in the backend today.
 */
private fun inBodyGroups(data: InBodyDataDto): List<FieldGroup> = listOf(
    FieldGroup(
        title = "Body Composition",
        rows = listOfNotNull(
            data.weightKg?.let { FieldRow("Weight", "%.1f kg".format(Locale.US, it)) },
            data.bmi?.let { FieldRow("BMI", "%.1f".format(Locale.US, it)) },
            data.bodyFatPercent?.let { FieldRow("Body fat %", "%.1f%%".format(Locale.US, it)) },
            data.bodyFatMassKg?.let { FieldRow("Body fat mass", "%.1f kg".format(Locale.US, it)) },
            data.skeletalMuscleMassKg?.let { FieldRow("Skeletal muscle mass", "%.1f kg".format(Locale.US, it)) },
            data.fatFreeMassKg?.let { FieldRow("Fat-free mass", "%.1f kg".format(Locale.US, it)) },
            data.inbodyScore?.let { FieldRow("InBody score", it.toString()) },
        ),
    ),
    FieldGroup(
        title = "Water & Mineral",
        rows = listOfNotNull(
            data.bodyWaterL?.let { FieldRow("Total body water", "%.1f L".format(Locale.US, it)) },
            data.intracellularWaterL?.let { FieldRow("Intracellular water", "%.1f L".format(Locale.US, it)) },
            data.extracellularWaterL?.let { FieldRow("Extracellular water", "%.1f L".format(Locale.US, it)) },
            data.proteinKg?.let { FieldRow("Protein", "%.1f kg".format(Locale.US, it)) },
            data.mineralKg?.let { FieldRow("Mineral", "%.1f kg".format(Locale.US, it)) },
        ),
    ),
    FieldGroup(
        title = "Metabolic & Targets",
        rows = listOfNotNull(
            data.basalMetabolicRate?.let { FieldRow("Basal metabolic rate", "%.0f kcal".format(Locale.US, it)) },
            data.visceralFatLevel?.let { FieldRow("Visceral fat level", "%.1f".format(Locale.US, it)) },
            data.waistHipRatio?.let { FieldRow("Waist-hip ratio", "%.2f".format(Locale.US, it)) },
            data.obesityDegreePercent?.let { FieldRow("Obesity degree", "%.1f%%".format(Locale.US, it)) },
            data.targetWeightKg?.let { FieldRow("Target weight", "%.1f kg".format(Locale.US, it)) },
            data.weightControlKg?.let { FieldRow("Weight control", "%.1f kg".format(Locale.US, it)) },
            data.fatControlKg?.let { FieldRow("Fat control", "%.1f kg".format(Locale.US, it)) },
            data.muscleControlKg?.let { FieldRow("Muscle control", "%.1f kg".format(Locale.US, it)) },
        ),
    ),
).filter { it.rows.isNotEmpty() }

@Composable
private fun FieldGroupCard(group: FieldGroup) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = group.title, style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.padding(top = 8.dp)) {
                group.rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = row.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = row.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
