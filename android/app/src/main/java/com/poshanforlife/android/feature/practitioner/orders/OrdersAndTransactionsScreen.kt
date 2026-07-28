package com.poshanforlife.android.feature.practitioner.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class OrdersTab { ORDERS, TRANSACTIONS }

/**
 * Hosts both OrdersScreen and TransactionsScreen behind one top segmented
 * toggle, rather than a second bottom-nav tab — the practitioner/admin
 * bottom nav is already at 7 items after AN-16's "Invoices" tab, and a
 * plain SingleChoiceSegmentedButtonRow (same pattern as AN-16's
 * CreateEstimateScreen subject-type toggle) fits both screens under the
 * existing "Orders" tab route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersAndTransactionsScreen(
    onOpenOrder: (orderId: String) -> Unit = {},
) {
    var tab by remember { mutableStateOf(OrdersTab.ORDERS) }

    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OrdersTab.entries.forEachIndexed { index, value ->
                SegmentedButton(
                    selected = tab == value,
                    onClick = { tab = value },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = OrdersTab.entries.size),
                ) { Text(if (value == OrdersTab.ORDERS) "Orders" else "Transactions") }
            }
        }

        when (tab) {
            OrdersTab.ORDERS -> OrdersScreen(onOpenOrder = onOpenOrder)
            OrdersTab.TRANSACTIONS -> TransactionsScreen()
        }
    }
}
