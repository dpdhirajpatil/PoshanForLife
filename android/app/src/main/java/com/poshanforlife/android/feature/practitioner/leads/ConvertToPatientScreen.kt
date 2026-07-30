package com.poshanforlife.android.feature.practitioner.leads

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CatalogueType
import com.poshanforlife.android.ui.components.AppDatePickerDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertToPatientScreen(
    modifier: Modifier = Modifier,
    viewModel: ConvertToPatientViewModel = hiltViewModel(),
    onConverted: (patientId: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val convertedPatientId by viewModel.convertedPatientId.collectAsStateWithLifecycle()

    LaunchedEffect(convertedPatientId) {
        convertedPatientId?.let(onConverted)
    }

    when (val state = uiState) {
        ConvertLeadUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ConvertLeadUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Couldn't load this lead: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        is ConvertLeadUiState.Form -> {
            var showStartDatePicker by remember { mutableStateOf(false) }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(text = "Convert to patient", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }

                item {
                    DetailCard {
                        Text(text = "Contact & medical details", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.phone,
                            onValueChange = viewModel::onPhoneChange,
                            label = { Text("Phone") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = viewModel::onEmailChange,
                            label = { Text("Email") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                            supportingText = { if (state.email.isBlank()) Text("Required if the lead has none on file") },
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.bloodGroup,
                                onValueChange = viewModel::onBloodGroupChange,
                                label = { Text("Blood group") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = state.heightCm,
                                onValueChange = viewModel::onHeightChange,
                                label = { Text("Height (cm)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }
                }

                item {
                    DetailCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Assign a service now", style = MaterialTheme.typography.titleLarge)
                            Switch(checked = state.assignService, onCheckedChange = viewModel::onAssignServiceToggle)
                        }

                        if (state.assignService) {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                CatalogueType.entries.forEachIndexed { index, type ->
                                    SegmentedButton(
                                        selected = state.serviceType == type,
                                        onClick = { viewModel.onServiceTypeChange(type) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = CatalogueType.entries.size),
                                    ) { Text(type.label) }
                                }
                            }

                            if (state.selectedService != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = state.selectedService.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    androidx.compose.material3.TextButton(onClick = viewModel::clearSelectedService) {
                                        Text("Change")
                                    }
                                }
                                OutlinedTextField(
                                    value = state.price,
                                    onValueChange = viewModel::onPriceChange,
                                    label = { Text("Price (₹)") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    singleLine = true,
                                )
                                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(text = "Starts ${state.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}")
                                }
                            } else {
                                OutlinedTextField(
                                    value = state.serviceSearch,
                                    onValueChange = viewModel::onServiceSearchChange,
                                    label = { Text("Search ${state.serviceType.label.lowercase()}s") },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    singleLine = true,
                                )
                                state.serviceResults.forEach { item ->
                                    CatalogueResultRow(item = item, onClick = { viewModel.onSelectService(item) })
                                }
                            }
                        }
                    }
                }

                if (state.submitError != null) {
                    item {
                        Text(
                            text = "Couldn't convert: ${state.submitError}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = viewModel::submit,
                            enabled = !state.submitting && state.name.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text(if (state.submitting) "Converting…" else "Confirm & convert") }
                    }
                }
            }

            if (showStartDatePicker) {
                AppDatePickerDialog(
                    initial = state.startDate,
                    onDismiss = { showStartDatePicker = false },
                    onConfirm = { viewModel.onStartDateChange(it); showStartDatePicker = false },
                )
            }
        }
    }
}

@Composable
private fun CatalogueResultRow(item: CatalogueItemDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
        item.priceInr?.let {
            Text(text = "₹%.0f".format(it), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
