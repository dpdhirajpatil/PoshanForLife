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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.poshanforlife.android.core.network.OrderListItemDto
import com.poshanforlife.android.ui.components.FilterDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class FilterOption(val label: String, val value: String?)

private val STATUS_OPTIONS = listOf(
    FilterOption("All statuses", null),
    FilterOption("Active", "active"),
    FilterOption("Completed", "completed"),
    FilterOption("Deactivated", "deactivated"),
)

private val PAYMENT_STATUS_OPTIONS = listOf(
    FilterOption("All payments", null),
    FilterOption("Paid", "paid"),
    FilterOption("Unpaid", "unpaid"),
    FilterOption("Pending", "pending"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
    onOpenOrder: (orderId: String) -> Unit = {},
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

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
        OutlinedTextField(
            value = filters.search,
            onValueChange = viewModel::onSearchChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search patient or order") },
            singleLine = true,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(STATUS_OPTIONS) { option ->
                FilterChip(
                    selected = filters.status == option.value,
                    onClick = { viewModel.onStatusFilterChange(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PAYMENT_STATUS_OPTIONS) { option ->
                FilterChip(
                    selected = filters.paymentStatus == option.value,
                    onClick = { viewModel.onPaymentStatusFilterChange(option.value) },
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
                OrderListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is OrderListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load orders: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                is OrderListUiState.Success -> {
                    if (state.orders.isEmpty()) {
                        EmptyOrdersState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.orders, key = { it.id }) { order ->
                                OrderCard(order = order, onClick = { onOpenOrder(order.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyOrdersState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No orders match these filters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun OrderCard(order: OrderListItemDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = order.patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = order.serviceName ?: "No service",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = createdAtLabel(order.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatOrderInr(order.amountInr), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LedgerBadge(text = order.paymentStatus)
                    LedgerBadge(text = order.status)
                }
            }
        }
    }
}

@Composable
internal fun LedgerBadge(text: String) {
    val (container, content) = when (text) {
        "paid", "active" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "pending" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "unpaid", "deactivated" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = text.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

internal fun formatOrderInr(amount: Double): String {
    val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
    format.maximumFractionDigits = 2
    return format.format(amount)
}

internal fun createdAtLabel(createdAt: String?): String {
    val instant = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
    return instant.atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
