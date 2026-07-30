package com.poshanforlife.android.feature.practitioner.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.ui.components.editableParsedDataList
import com.poshanforlife.android.ui.theme.BrandGold300
import com.poshanforlife.android.ui.theme.BrandGreenDarkest
import com.poshanforlife.android.ui.theme.BrandGreenLightest
import com.poshanforlife.android.ui.theme.BrandNavyDarkest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportUploadViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val reviewState by viewModel.reviewState.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val editedData by viewModel.editedData.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val confirmState by viewModel.confirmState.collectAsStateWithLifecycle()

    LaunchedEffect(confirmState) {
        if (confirmState is ConfirmState.Saved) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Review report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = reviewState) {
            ReviewUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is ReviewUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Couldn't load this report: ${state.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            ReviewUiState.Success -> {
                val extractedFieldCount = report?.extractedFieldCount ?: editedData.nonNullFieldCount()
                val tier = confidenceTierFor(extractedFieldCount)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item { ConfidenceBanner(tier = tier, extractedFieldCount = extractedFieldCount, onRetake = onBack) }
                    item { ExtractionTag() }

                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = viewModel::onTitleChange,
                            label = { Text("Report title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = viewModel::onNotesChange,
                            label = { Text("Practitioner notes") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    editableParsedDataList(
                        data = editedData,
                        onFieldChange = viewModel::onFieldChange,
                    )

                    item {
                        Column {
                            Button(
                                onClick = viewModel::confirm,
                                enabled = confirmState !is ConfirmState.Saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (confirmState is ConfirmState.Saving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Text("Confirm and save")
                                }
                            }
                            if (confirmState is ConfirmState.Error) {
                                Text(
                                    text = (confirmState as ConfirmState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class BannerSpec(val container: androidx.compose.ui.graphics.Color, val content: androidx.compose.ui.graphics.Color, val headline: String)

@Composable
private fun ConfidenceBanner(tier: ConfidenceTier, extractedFieldCount: Int, onRetake: () -> Unit) {
    val spec = when (tier) {
        ConfidenceTier.HIGH -> BannerSpec(BrandGreenLightest, BrandGreenDarkest, "Ready to save")
        ConfidenceTier.MEDIUM -> BannerSpec(BrandGold300, BrandNavyDarkest, "Please review the values below")
        ConfidenceTier.LOW -> BannerSpec(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Low-confidence extraction")
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = spec.container, contentColor = spec.content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = spec.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "$extractedFieldCount of $REPORT_FIELD_COUNT fields extracted",
                style = MaterialTheme.typography.bodySmall,
            )
            if (tier == ConfidenceTier.LOW) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onRetake) { Text("Retake photo") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractionTag() {
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Extracted using Claude Haiku AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text("Values are extracted automatically by AI from the photo and may need correction.") } },
            state = tooltipState,
        ) {
            IconButton(onClick = { scope.launch { tooltipState.show() } }, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "About this extraction",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

