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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.LeadListItemDto
import com.poshanforlife.android.core.network.LeadSummaryDto

private data class StageOption(val label: String, val value: String?)

private val STAGE_OPTIONS = listOf(
    StageOption("All", null),
    StageOption("New", "new"),
    StageOption("Contacted", "contacted"),
    StageOption("Qualified", "qualified"),
    StageOption("Proposed", "proposed"),
    StageOption("Converted", "converted"),
    StageOption("Lost", "lost"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadListScreen(
    modifier: Modifier = Modifier,
    viewModel: LeadsViewModel = hiltViewModel(),
    onOpenLead: (leadId: String) -> Unit = {},
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    val stageFilter by viewModel.stageFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search leads") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(STAGE_OPTIONS) { option ->
                FilterChip(
                    selected = stageFilter == option.value,
                    onClick = { viewModel.onStageFilterChange(option.value) },
                    label = { Text(option.label) },
                )
            }
        }

        (uiState as? LeadListUiState.Success)?.summary?.let { summary ->
            SummaryStatRow(summary = summary)
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshList,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                LeadListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is LeadListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load leads: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                is LeadListUiState.Success -> {
                    if (state.leads.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.leads, key = { it.id }) { lead ->
                                LeadCard(lead = lead, onClick = { onOpenLead(lead.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatRow(summary: LeadSummaryDto) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            StatCard(
                icon = Icons.Filled.CalendarToday,
                label = "Follow-ups today",
                value = summary.followupToday.toString(),
            )
        }
        item {
            StatCard(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                label = "Conversion rate",
                value = "${summary.conversionRate.toInt()}%",
            )
        }
        item {
            StatCard(
                icon = Icons.Filled.ContactPhone,
                label = "New this week",
                value = summary.newThisWeek.toString(),
            )
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.PersonSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = "No leads yet",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun LeadCard(lead: LeadListItemDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = lead.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = lead.phone ?: lead.email ?: "No contact info",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lead.interestedProgramme?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StageBadge(stage = lead.stage)
        }
    }
}

@Composable
internal fun StageBadge(stage: String) {
    val (container, content) = when (stage) {
        "converted" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "lost" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "qualified", "proposed" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = stage.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
