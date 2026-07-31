package com.poshanforlife.android.feature.lead

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorWeight
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
import com.poshanforlife.android.core.network.LeadStreakDto
import com.poshanforlife.android.core.network.PatientBadgeStatusDto
import com.poshanforlife.android.feature.patient.track.TrackViewModel
import com.poshanforlife.android.ui.components.BadgeRow
import com.poshanforlife.android.ui.components.ProgressRing
import com.poshanforlife.android.ui.components.StreakChip
import java.time.LocalDate

private val HEALTH_TIPS = listOf(
    "Drink a glass of water first thing in the morning to kickstart your metabolism.",
    "Aim for at least 7-8 hours of sleep — recovery matters as much as activity.",
    "A short 10-minute walk after meals can help with digestion and blood sugar.",
    "Protein at breakfast keeps you fuller for longer through the day.",
    "Consistency beats intensity — small daily habits add up more than occasional big efforts.",
)

@Composable
fun LeadHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: LeadHomeViewModel = hiltViewModel(),
    trackViewModel: TrackViewModel = hiltViewModel(),
    onRequestConsultation: () -> Unit = {},
) {
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val gamification by viewModel.gamification.collectAsStateWithLifecycle()
    val trackState by trackViewModel.uiState.collectAsStateWithLifecycle()
    val tip = HEALTH_TIPS[LocalDate.now().dayOfYear % HEALTH_TIPS.size]

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (userName.isNotBlank()) "Hi, $userName" else "Welcome",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                gamification.streak?.let { streak -> StreakChip(days = streak.currentStreak) }
            }
        }

        if (!gamification.loading) {
            item {
                GamificationCard(
                    streak = gamification.streak,
                    badges = gamification.badges,
                    checkingIn = gamification.checkingIn,
                    onCheckIn = viewModel::checkInToday,
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Ready to talk to a practitioner?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Request a consultation and we'll get you set up with a personalized plan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Button(onClick = onRequestConsultation, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Request consultation")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Today", style = MaterialTheme.typography.titleLarge)
                    SummaryRow(
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        label = "Steps",
                        value = trackState.stepsToday?.toString() ?: "—",
                    )
                    SummaryRow(
                        icon = Icons.Filled.LocalDrink,
                        label = "Water",
                        value = "${trackState.waterLoggedMl} ml",
                    )
                    trackState.sleepHoursToday?.let {
                        SummaryRow(icon = Icons.Filled.Favorite, label = "Sleep", value = "${it}h")
                    }
                    trackState.latestWeightKg?.let {
                        SummaryRow(icon = Icons.Filled.MonitorWeight, label = "Weight", value = "${it} kg")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = "Unlock full body composition tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Request a consultation to get InBody reports and a practitioner-guided programme.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/** AN-22: streak ring + badge row + check-in CTA — the concrete gamified components for PoshanLeadTheme's "gamified" direction. */
@Composable
private fun GamificationCard(
    streak: LeadStreakDto?,
    badges: List<PatientBadgeStatusDto>,
    checkingIn: Boolean,
    onCheckIn: () -> Unit,
) {
    val checkedInToday = streak?.lastLoggedDate == LocalDate.now().toString()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Your streak", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(percentComplete = streak?.percentComplete ?: 0)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = "Best: ${streak?.longestStreak ?: 0} days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Log your health data daily to build your streak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (badges.isNotEmpty()) {
                BadgeRow(badges = badges, modifier = Modifier.padding(top = 16.dp))
            }

            if (checkedInToday) {
                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Checked in today")
                }
            } else {
                Button(onClick = onCheckIn, enabled = !checkingIn, modifier = Modifier.padding(top = 16.dp)) {
                    if (checkingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Check in today")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
