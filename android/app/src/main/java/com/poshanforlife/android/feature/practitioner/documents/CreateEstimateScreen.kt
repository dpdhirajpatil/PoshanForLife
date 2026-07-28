package com.poshanforlife.android.feature.practitioner.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.LeadPickerItemDto
import com.poshanforlife.android.core.network.PatientSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEstimateScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateEstimateViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: (documentId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveState) {
        val state = uiState.saveState
        if (state is EstimateSaveState.Success) onSaved(state.documentId)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(text = "New estimate", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        item { SubjectSection(uiState = uiState, viewModel = viewModel) }

        item {
            Text(text = "Line items", style = MaterialTheme.typography.titleMedium)
        }

        items(uiState.items, key = { it.id }) { item ->
            LineItemRow(
                item = item,
                onChange = { transform -> viewModel.updateItem(item.id, transform) },
                onRemove = { viewModel.removeItem(item.id) },
                canRemove = uiState.items.size > 1,
            )
        }

        item {
            OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(text = "Add item", modifier = Modifier.padding(start = 8.dp))
            }
        }

        item {
            OutlinedTextField(
                value = uiState.discountInr,
                onValueChange = viewModel::onDiscountChange,
                label = { Text("Discount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }

        item { PreviewTotalsCard(totals = uiState.previewTotals) }

        if (uiState.saveState is EstimateSaveState.Error) {
            item {
                Text(
                    text = "Couldn't save: ${(uiState.saveState as EstimateSaveState.Error).message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            val isSaving = uiState.saveState is EstimateSaveState.Saving
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = viewModel::saveDraft,
                    enabled = uiState.canSave && !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Save as draft") }
                Button(
                    onClick = viewModel::saveAndSend,
                    enabled = uiState.canSave && !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text(if (isSaving) "Saving…" else "Save & send") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectSection(uiState: CreateEstimateUiState, viewModel: CreateEstimateViewModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (uiState.selectedSubjectName != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = "Billed to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = uiState.selectedSubjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = viewModel::clearSubject) { Text("Change") }
                }
                return@Column
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EstimateSubjectType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = uiState.subjectType == type,
                        onClick = { viewModel.onSubjectTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = EstimateSubjectType.entries.size),
                    ) { Text(if (type == EstimateSubjectType.PATIENT) "Patient" else "Lead") }
                }
            }

            OutlinedTextField(
                value = uiState.subjectSearch,
                onValueChange = viewModel::onSubjectSearchChange,
                label = { Text(if (uiState.subjectType == EstimateSubjectType.PATIENT) "Search patients" else "Search leads") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
            )

            if (uiState.isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
            } else if (uiState.subjectType == EstimateSubjectType.PATIENT) {
                uiState.patientResults.forEach { patient -> PatientResultRow(patient) { viewModel.selectSubject(patient.id, patient.name) } }
            } else {
                uiState.leadResults.forEach { lead -> LeadResultRow(lead) { viewModel.selectSubject(lead.id, lead.name) } }
            }
        }
    }
}

@Composable
private fun PatientResultRow(patient: PatientSummaryDto, onClick: () -> Unit) {
    Text(
        text = patient.name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun LeadResultRow(lead: LeadPickerItemDto, onClick: () -> Unit) {
    Text(
        text = lead.name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun LineItemRow(
    item: EstimateLineItemUi,
    onChange: ((EstimateLineItemUi) -> EstimateLineItemUi) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = item.itemName,
                    onValueChange = { value -> onChange { it.copy(itemName = value) } },
                    label = { Text("Item name") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove item")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = item.hsnSac,
                    onValueChange = { value -> onChange { it.copy(hsnSac = value) } },
                    label = { Text("HSN/SAC") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.quantity,
                    onValueChange = { value -> onChange { it.copy(quantity = value) } },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.rate,
                    onValueChange = { value -> onChange { it.copy(rate = value) } },
                    label = { Text("Rate (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun PreviewTotalsCard(totals: EstimatePreviewTotals) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PreviewRow("Subtotal", totals.subtotal)
            PreviewRow("CGST (2.5%)", totals.cgst)
            PreviewRow("SGST (2.5%)", totals.sgst)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PreviewRow("Total", totals.total, emphasize = true)
        }
    }
}

@Composable
private fun PreviewRow(label: String, amount: Double, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = formatInr(amount),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
