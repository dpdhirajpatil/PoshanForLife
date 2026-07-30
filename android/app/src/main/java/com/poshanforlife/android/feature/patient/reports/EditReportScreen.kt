package com.poshanforlife.android.feature.patient.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.ui.components.editableParsedDataList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReportScreen(
    modifier: Modifier = Modifier,
    viewModel: EditReportViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val editedData by viewModel.editedData.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    LaunchedEffect(saveState) {
        if (saveState is EditReportSaveState.Saved) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Edit report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = uiState) {
            EditReportUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is EditReportUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load this report: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            is EditReportUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text("Notes") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        StatusDropdown(status = status, onStatusChange = viewModel::onStatusChange)
                    }

                    if (state.type == "inbody") {
                        editableParsedDataList(data = editedData, onFieldChange = viewModel::onFieldChange)
                    }

                    item {
                        Button(
                            onClick = viewModel::save,
                            enabled = saveState !is EditReportSaveState.Saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (saveState is EditReportSaveState.Saving) "Saving…" else "Save")
                        }
                    }
                    if (saveState is EditReportSaveState.Error) {
                        item {
                            Text(
                                text = (saveState as EditReportSaveState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusDropdown(status: String, onStatusChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = status.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EDITABLE_REPORT_STATUSES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onStatusChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
