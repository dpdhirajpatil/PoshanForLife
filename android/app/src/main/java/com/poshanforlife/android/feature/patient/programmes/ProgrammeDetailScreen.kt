package com.poshanforlife.android.feature.patient.programmes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.network.ChallengeProgressDto
import java.time.LocalDate
import java.util.Locale

@Composable
fun ProgrammeDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgrammeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        ProgrammeDetailUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ProgrammeDetailUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Couldn't load this programme: ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        is ProgrammeDetailUiState.Success -> {
            val programme = state.programme
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column {
                        Text(
                            // ALL CAPS per Type.kt's headline contract (Patient theme).
                            text = (programme.catalogueItem?.name ?: "Service").uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        programme.assignedDoctor?.let {
                            Text(
                                text = "with ${it.name}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    DetailCard {
                        DetailRow("Status", programme.status.replaceFirstChar { it.uppercase() })
                        DetailRow("Start date", programme.startDate ?: "—")
                        DetailRow("End date", programme.endDate ?: "—")
                        programme.priceInr?.let { DetailRow("Price", "₹%.2f".format(Locale.US, it)) }
                    }
                }

                if (!programme.notes.isNullOrBlank()) {
                    item {
                        DetailCard {
                            Text(text = "Notes", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = programme.notes,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                if (programme.serviceType == "challenge") {
                    item {
                        ChallengeProgressCard(
                            progress = state.challengeProgress,
                            checkingIn = state.checkingIn,
                            onCheckIn = { viewModel.checkInToday() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeProgressCard(
    progress: ChallengeProgressDto?,
    checkingIn: Boolean,
    onCheckIn: () -> Unit,
) {
    val checkedInToday = progress?.lastLoggedDate == LocalDate.now().toString()

    DetailCard {
        Text(text = "Challenge progress", style = MaterialTheme.typography.titleLarge)

        if (progress == null) {
            Text(
                text = "Loading progress…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@DetailCard
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                CircularProgressIndicator(
                    progress = { progress.percentComplete / 100f },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                )
                Text(text = "${progress.percentComplete}%", style = MaterialTheme.typography.labelLarge)
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = " ${progress.currentStreak} day streak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "Best: ${progress.longestStreak} days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (checkedInToday) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = " Checked in today", modifier = Modifier.padding(start = 4.dp))
            }
        } else {
            Button(
                onClick = onCheckIn,
                enabled = !checkingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(text = if (checkingIn) "Checking in…" else "Check in today")
            }
        }
    }
}

@Composable
private fun DetailCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
