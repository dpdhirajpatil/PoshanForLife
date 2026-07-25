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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.InBodyDataDto
import java.util.Locale

@Composable
fun ReportDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when (val state = uiState) {
        ReportDetailUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ReportDetailUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column {
                        Text(text = report.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            }
        }
    }
}

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
