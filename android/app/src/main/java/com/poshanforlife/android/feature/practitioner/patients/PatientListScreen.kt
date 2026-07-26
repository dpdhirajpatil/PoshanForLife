package com.poshanforlife.android.feature.practitioner.patients

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.PatientSummaryDto
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    modifier: Modifier = Modifier,
    viewModel: PatientManagementViewModel = hiltViewModel(),
    onOpenPatient: (patientId: String) -> Unit = {},
) {
    val uiState by viewModel.listState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search patients") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshList,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val state = uiState) {
                PatientListUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PatientListUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Couldn't load patients: ${state.message}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }

                is PatientListUiState.Success -> {
                    if (state.patients.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.patients, key = { it.id }) { patient ->
                                PatientCard(patient = patient, onClick = { onOpenPatient(patient.id) })
                            }
                        }
                    }
                }
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
                text = "No patients assigned yet",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun PatientCard(patient: PatientSummaryDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = patient.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ageLabel(patient.dateOfBirth),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = lastReportLabel(patient.lastReportDate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ageLabel(dateOfBirth: String?): String {
    val dob = dateOfBirth?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "Age unknown"
    val years = Period.between(dob, LocalDate.now()).years
    return "$years yrs"
}

private fun lastReportLabel(lastReportDate: String?): String {
    val instant = lastReportDate?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return "No reports"
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
