package com.poshanforlife.android.feature.practitioner.patients

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.poshanforlife.android.feature.practitioner.documents.formatInr
import com.poshanforlife.android.ui.components.AppDatePickerDialog
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignServiceScreen(
    modifier: Modifier = Modifier,
    viewModel: AssignServiceViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onAssigned: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        AssignServiceUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is AssignServiceUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Couldn't load this service: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        is AssignServiceUiState.Form -> {
            var showStartDatePicker by remember { mutableStateOf(false) }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(text = "Assign service", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }

                item {
                    DetailCard {
                        Text(text = state.item.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${state.type.label} for ${state.patientName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    DetailCard {
                        Text(text = "Details", style = MaterialTheme.typography.titleLarge)
                        OutlinedButton(
                            onClick = { showStartDatePicker = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text(text = "Starts ${state.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}")
                        }
                        OutlinedTextField(
                            value = state.price,
                            onValueChange = viewModel::onPriceChange,
                            label = { Text("Price (₹)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            minLines = 3,
                        )
                    }
                }

                item {
                    DetailCard {
                        Text(text = "Summary", style = MaterialTheme.typography.titleLarge)
                        SummaryRow("Service", state.item.name)
                        SummaryRow("Patient", state.patientName)
                        SummaryRow("Start date", state.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                        SummaryRow("Price", state.price.toDoubleOrNull()?.let { formatInr(it) } ?: "—")
                    }
                }

                if (state.submitError != null) {
                    item {
                        Column {
                            Text(
                                text = "Couldn't assign: ${state.submitError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onBack) { Text("Choose a different service") }
                        }
                    }
                }

                item {
                    Button(
                        onClick = viewModel::confirm,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.submitting) "Assigning…" else "Confirm assignment") }
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

        is AssignServiceUiState.Confirmed -> {
            val assignment = state.assignment
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Text(text = "Assignment confirmed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = assignment.catalogueItem?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                )
                assignment.order?.let { order ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Order created", style = MaterialTheme.typography.titleMedium)
                            SummaryRow("Amount", formatInr(order.amountInr))
                            SummaryRow("Payment status", order.paymentStatus.replaceFirstChar { it.uppercase() })
                            SummaryRow("Order status", order.status.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Button(onClick = onAssigned, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
