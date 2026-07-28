package com.poshanforlife.android.feature.practitioner.orders

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.poshanforlife.android.core.network.OrderDetailDto
import com.poshanforlife.android.core.network.OrderProgrammeDto
import com.poshanforlife.android.core.network.OrderTransactionSummaryDto

@Composable
fun OrderDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.detailState.collectAsStateWithLifecycle()
    val markAsPaidState by viewModel.markAsPaidState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Mark as paid?") },
            text = { Text("This will generate a transaction and an invoice number for this order. This can't be undone from here.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.confirmMarkAsPaid()
                }) { Text("Mark as paid") }
            },
            dismissButton = { TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") } },
        )
    }

    when (val state = uiState) {
        OrderDetailUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is OrderDetailUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Couldn't load this order: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        is OrderDetailUiState.Success -> {
            OrderDetailContent(
                order = state.order,
                markAsPaidState = markAsPaidState,
                onMarkAsPaid = { showConfirmDialog = true },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun OrderDetailContent(
    order: OrderDetailDto,
    markAsPaidState: MarkAsPaidState,
    onMarkAsPaid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(text = order.patient.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = formatOrderInr(order.amountInr),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    LedgerBadge(text = order.paymentStatus)
                    LedgerBadge(text = order.status)
                }
            }
        }

        if (order.patientProgramme != null) {
            item { AssignmentCard(programme = order.patientProgramme) }
        }

        if (!order.notes.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Notes", style = MaterialTheme.typography.titleMedium)
                        Text(text = order.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        if (order.transactions.isNotEmpty()) {
            item { TransactionsLedgerCard(transactions = order.transactions) }
        }

        if (order.paymentStatus != "paid") {
            item {
                Button(
                    onClick = onMarkAsPaid,
                    enabled = markAsPaidState !is MarkAsPaidState.Saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (markAsPaidState is MarkAsPaidState.Saving) "Saving…" else "Mark as paid")
                }
            }
            if (markAsPaidState is MarkAsPaidState.Error) {
                item {
                    Text(
                        text = "Couldn't mark as paid: ${markAsPaidState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentCard(programme: OrderProgrammeDto) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Assignment", style = MaterialTheme.typography.titleMedium)
            InfoRow("Service", programme.catalogueItem?.name ?: "—")
            InfoRow("Type", programme.serviceType?.replaceFirstChar { it.uppercase() } ?: "—")
            InfoRow("Status", programme.status?.replaceFirstChar { it.uppercase() } ?: "—")
            InfoRow("Duration", durationLabel(programme))
            InfoRow("Start date", programme.startDate ?: "—")
            InfoRow("End date", programme.endDate ?: "—")
            InfoRow("Assigned by", programme.assignedBy?.name ?: "—")
            InfoRow("Practitioner", programme.assignedDoctor?.name ?: "—")
        }
    }
}

private fun durationLabel(programme: OrderProgrammeDto): String {
    val item = programme.catalogueItem ?: return "—"
    return when {
        item.durationWeeks != null -> "${item.durationWeeks} weeks"
        item.durationMinutes != null -> "${item.durationMinutes} minutes"
        item.durationDays != null -> "${item.durationDays} days"
        else -> "—"
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TransactionsLedgerCard(transactions: List<OrderTransactionSummaryDto>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Transactions", style = MaterialTheme.typography.titleMedium)
            transactions.forEachIndexed { index, tx ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(text = tx.invoiceNumber ?: tx.transactionId, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${tx.transactionType.replaceFirstChar { it.uppercase() }} · ${tx.paymentType.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(text = formatOrderInr(tx.amountInr), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
