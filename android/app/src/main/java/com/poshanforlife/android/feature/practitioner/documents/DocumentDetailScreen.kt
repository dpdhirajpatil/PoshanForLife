package com.poshanforlife.android.feature.practitioner.documents

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import com.poshanforlife.android.core.network.DocumentDetailDto
import com.poshanforlife.android.core.network.DocumentItemDto

@Composable
fun DocumentDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: DocumentsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.detailState.collectAsStateWithLifecycle()
    val pdfShareState by viewModel.pdfShareState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(pdfShareState) {
        val state = pdfShareState
        if (state is PdfShareState.Ready) {
            // Generic share sheet over the signed link — a WhatsApp-specific deep
            // link (matching the web app's WhatsApp share) is a follow-on, not this pass.
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, state.url)
            }
            context.startActivity(Intent.createChooser(intent, "Share document"))
            viewModel.consumePdfShareEvent()
        }
    }

    when (val state = uiState) {
        DocumentDetailUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is DocumentDetailUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Couldn't load this document: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        is DocumentDetailUiState.Success -> {
            DocumentDetailContent(
                document = state.document,
                isSharing = pdfShareState is PdfShareState.Loading,
                onSharePdf = viewModel::sharePdf,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DocumentDetailContent(
    document: DocumentDetailDto,
    isSharing: Boolean,
    onSharePdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = document.documentNumber,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = (document.patient?.name ?: document.lead?.name).orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(status = document.status)
            }
        }

        item { LineItemsCard(items = document.items) }

        item { TotalsCard(document = document) }

        if (!document.notes.isNullOrBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Notes", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = document.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSharePdf,
                enabled = !isSharing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShareIcon()
                Text(text = if (isSharing) "Preparing…" else "Share PDF", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ShareIcon() {
    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
}

@Composable
private fun LineItemsCard(items: List<DocumentItemDto>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Items", style = MaterialTheme.typography.titleMedium)
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.itemName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        val meta = listOfNotNull(
                            item.hsnSac?.let { "HSN/SAC: $it" },
                            "Qty ${item.quantity} × ${formatInr(item.rateInr)}",
                        ).joinToString(" · ")
                        Text(text = meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(text = formatInr(item.lineTotal), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(document: DocumentDetailDto) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TotalRow("Subtotal", document.subtotal)
            if (document.discountInr > 0) TotalRow("Discount", -document.discountInr)
            TotalRow("CGST (2.5%)", document.cgstAmount)
            TotalRow("SGST (2.5%)", document.sgstAmount)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TotalRow("Total", document.total, emphasize = true)
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, emphasize: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            text = formatInr(amount),
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
