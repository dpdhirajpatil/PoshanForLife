package com.poshanforlife.android.feature.practitioner.orders

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.poshanforlife.android.core.network.TransactionListItemDto
import com.poshanforlife.android.core.network.TransactionTotalsDto
import com.poshanforlife.android.ui.components.FilterDatePickerDialog
import java.time.LocalDate

private data class TxFilterOption(val label: String, val value: String?)

private val CATALOGUE_OPTIONS = listOf(
    TxFilterOption("All types", null),
    TxFilterOption("Programme", "programme"),
    TxFilterOption("Session", "session"),
    TxFilterOption("Challenge", "challenge"),
)

private val PAYMENT_TYPE_OPTIONS = listOf(
    TxFilterOption("All payment types", null),
    TxFilterOption("Offline", "offline"),
    TxFilterOption("Online", "online"),
    TxFilterOption("Credit", "credit"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val practitioners by viewModel.practitioners.collectAsStateWithLifecycle()

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }
    var showPractitionerMenu by remember { mutableStateOf(false) }

    if (showFromPicker) {
        FilterDatePickerDialog(
            initial = filters.dateFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { showFromPicker = false },
            onConfirm = { date ->
                showFromPicker = false
                viewModel.onDateRangeChange(dateFrom = date.toString(), dateTo = filters.dateTo)
            },
        )
    }
    if (showToPicker) {
        FilterDatePickerDialog(
            initial = filters.dateTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            onDismiss = { showToPicker = false },
            onConfirm = { date ->
                showToPicker = false
                viewModel.onDateRangeChange(dateFrom = filters.dateFrom, dateTo = date.toString())
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (uiState is TransactionListUiState.Success) {
            SummaryRow(summary = (uiState as TransactionListUiState.Success).summary)
        }

        if (isAdmin) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(onClick = { showPractitionerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(filters.practitionerName ?: "All practitioners")
                }
                DropdownMenu(expanded = showPractitionerMenu, onDismissRequest = { showPractitionerMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("All practitioners") },
                        onClick = {
                            showPractitionerMenu = false
                            viewModel.onPractitionerFilterChange(null, null)
                        },
                    )
                    practitioners.forEach { practitioner ->
                        DropdownMenuItem(
                            text = { Text(practitioner.name) },
                            onClick = {
                                showPractitionerMenu = false
                                viewModel.onPractitionerFilterChange(practitioner.id, practitioner.name)
                            },
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CATALOGUE_OPTIONS) { option ->
                FilterChip(
                    selected = filters.catalogue == option.value,
                    onClick = { viewModel.onCatalogueFilterChange(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PAYMENT_TYPE_OPTIONS) { option ->
                FilterChip(
                    selected = filters.paymentType == option.value,
                    onClick = { viewModel.onPaymentTypeFilterChange(option.value) },
                    label = { Text(option.label) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showFromPicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Text(text = filters.dateFrom ?: "From date", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { showToPicker = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                Text(text = filters.dateTo ?: "To date", modifier = Modifier.padding(start = 8.dp))
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshList,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                TransactionListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is TransactionListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load transactions: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                is TransactionListUiState.Success -> {
                    if (state.transactions.isEmpty()) {
                        EmptyTransactionsState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.transactions, key = { it.id }) { tx -> TransactionCard(tx) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(summary: TransactionTotalsDto) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(label = "Total transaction value", amount = summary.totalTransactionValue, modifier = Modifier.weight(1f))
        SummaryCard(label = "Total credit consumed", amount = summary.totalCreditConsumed, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCard(label: String, amount: Double, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = formatOrderInr(amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyTransactionsState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No transactions match these filters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun TransactionCard(tx: TransactionListItemDto) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = tx.invoiceNumber ?: tx.transactionId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = tx.patient.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${tx.serviceName ?: "No service"} · ${createdAtLabel(tx.createdAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatOrderInr(tx.amountInr), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LedgerBadge(text = tx.transactionType)
                    LedgerBadge(text = tx.paymentType)
                }
            }
        }
    }
}
