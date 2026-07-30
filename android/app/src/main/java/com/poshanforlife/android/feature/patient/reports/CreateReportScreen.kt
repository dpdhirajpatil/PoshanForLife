package com.poshanforlife.android.feature.patient.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateReportViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: (reportId: String) -> Unit = {},
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()

    LaunchedEffect(saveState) {
        (saveState as? CreateReportSaveState.Saved)?.let { onSaved(it.reportId) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("New report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TypeDropdown(type = type, onTypeChange = viewModel::onTypeChange)
            OutlinedTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notes (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::save,
                enabled = title.isNotBlank() && saveState !is CreateReportSaveState.Saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saveState is CreateReportSaveState.Saving) "Saving…" else "Save")
            }
            if (saveState is CreateReportSaveState.Error) {
                Text(
                    text = (saveState as CreateReportSaveState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(type: String, onTypeChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = type.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MANUAL_REPORT_TYPES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onTypeChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
