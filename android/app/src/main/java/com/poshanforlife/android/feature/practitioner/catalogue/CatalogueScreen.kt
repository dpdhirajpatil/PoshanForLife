package com.poshanforlife.android.feature.practitioner.catalogue

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.poshanforlife.android.feature.practitioner.documents.formatInr

private data class StatusOption(val label: String, val value: String?)

private val STATUS_OPTIONS = listOf(
    StatusOption("All", null),
    StatusOption("Draft", "draft"),
    StatusOption("Published", "published"),
    StatusOption("Archived", "archived"),
)

/**
 * Shared browse screen for all three catalogue types, reused three ways:
 * a plain read-only browse (practitioners), the same browse plus create/edit
 * (admins, via [isAdmin]), and a picker (assigning a service to a patient —
 * AN-09's patient detail, AN-14's convert flow — via [pickerMode]/[onItemSelected]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueScreen(
    modifier: Modifier = Modifier,
    viewModel: CatalogueViewModel = hiltViewModel(),
    isAdmin: Boolean = false,
    pickerMode: Boolean = false,
    onItemSelected: (CatalogueItemDto) -> Unit = {},
    onCreateItem: (type: CatalogueType) -> Unit = { _ -> },
    onEditItem: (type: CatalogueType, id: String) -> Unit = { _, _ -> },
) {
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var detailItem by remember { mutableStateOf<CatalogueItemDto?>(null) }

    // AN-20: assigning a service should only ever offer live, bookable items.
    LaunchedEffect(pickerMode) {
        if (pickerMode) viewModel.onStatusFilterChange("published")
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (isAdmin && !pickerMode) {
                FloatingActionButton(onClick = { onCreateItem(selectedType) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add service")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedType.ordinal) {
                CatalogueType.entries.forEach { type ->
                    Tab(
                        selected = selectedType == type,
                        onClick = { viewModel.onTypeChange(type) },
                        text = { Text(type.label + "s") },
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search ${selectedType.label.lowercase()}s") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            if (!pickerMode) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(STATUS_OPTIONS) { option ->
                        FilterChip(
                            selected = statusFilter == option.value,
                            onClick = { viewModel.onStatusFilterChange(option.value) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            ) {
                when (val state = listState) {
                    CatalogueListUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is CatalogueListUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Couldn't load the catalogue: ${state.message}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }

                    is CatalogueListUiState.Success -> {
                        if (state.items.isEmpty()) {
                            EmptyState()
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.items, key = { it.id }) { item ->
                                    CatalogueItemCard(
                                        item = item,
                                        onClick = {
                                            when {
                                                pickerMode -> onItemSelected(item)
                                                isAdmin -> onEditItem(selectedType, item.id)
                                                else -> detailItem = item
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailItem = null },
            confirmButton = { TextButton(onClick = { detailItem = null }) { Text("Close") } },
            title = { Text(item.name) },
            text = {
                Text(
                    text = item.goalDescription?.takeIf { it.isNotBlank() }
                        ?: item.description?.takeIf { it.isNotBlank() }
                        ?: "No description yet.",
                )
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No services found",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
internal fun CatalogueItemCard(item: CatalogueItemDto, onClick: () -> Unit) {
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
                Text(text = item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = durationLabel(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                item.priceInr?.let {
                    Text(text = formatInr(it), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                CatalogueStatusBadge(status = item.status)
            }
        }
    }
}

@Composable
private fun CatalogueStatusBadge(status: String) {
    val (container, content) = when (status) {
        "published" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "archived" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun durationLabel(item: CatalogueItemDto): String = when {
    item.durationWeeks != null -> "${item.durationWeeks} weeks"
    item.durationMinutes != null -> "${item.durationMinutes} min"
    item.durationDays != null -> "${item.durationDays} days"
    else -> "—"
}
