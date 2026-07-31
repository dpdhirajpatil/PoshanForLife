package com.poshanforlife.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class MoreMenuItem(val label: String, val icon: ImageVector, val route: String)

/** A "More" overflow tab's content — a plain row list, no trapezium/staff-theme-forbidden flourish. Practitioner-only today (Admin uses a drawer instead of an overflow tab). */
@Composable
fun MoreMenuScreen(items: List<MoreMenuItem>, onSelect: (route: String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(item.label) },
                leadingContent = { Icon(item.icon, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item.route) }
                    .padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
    }
}
