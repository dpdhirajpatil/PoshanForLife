package com.poshanforlife.android.feature.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.poshanforlife.android.core.reminder.nextOccurrenceLabel
import com.poshanforlife.android.core.reminder.nextOccurrenceOf
import com.poshanforlife.android.feature.patient.track.ReminderViewModel
import com.poshanforlife.android.core.network.DocumentListItemDto
import com.poshanforlife.android.core.network.HealthRecordDto
import com.poshanforlife.android.core.network.PatientProgrammeDto
import com.poshanforlife.android.core.network.UserDetailDto
import com.poshanforlife.android.ui.components.shimmerPlaceholder
import com.poshanforlife.android.ui.theme.TrapeziumSectionLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    onViewFullReport: () -> Unit = {},
    onPayInvoice: (documentId: String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { GreetingHeader(uiState.profile) }

            item { InBodyScoreCard(state = uiState.healthRecord, onViewFullReport = onViewFullReport) }

            val programmeState = uiState.programme
            if (programmeState is CardState.Success && programmeState.data != null) {
                item {
                    // Direction A's signature pattern: the trapezium label chip sits
                    // ABOVE the card rather than being a header row inside it.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrapeziumSectionLabel("Active programme")
                        ActiveProgrammeCard(programmeState.data)
                    }
                }
            }

            val invoicesState = uiState.invoices
            if (invoicesState is CardState.Success && invoicesState.data.isNotEmpty()) {
                item { OutstandingBalanceCard(invoicesState.data, onPayInvoice) }
            }

            item { UpcomingRemindersSection() }
        }
    }
}

@Composable
private fun GreetingHeader(state: CardState<UserDetailDto>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            is CardState.Success -> {
                val firstName = state.data.name.trim().substringBefore(" ")
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${greeting()}, $firstName", style = MaterialTheme.typography.titleLarge)
                }
                Avatar(name = state.data.name, avatarUrl = state.data.avatarUrl)
            }
            is CardState.Error -> Text(text = "Welcome back", style = MaterialTheme.typography.titleLarge)
            CardState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun Avatar(name: String, avatarUrl: String?, size: Dp = 48.dp) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        val initials = name.trim().split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun DashboardCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun InBodyScoreCard(state: CardState<HealthRecordDto?>, onViewFullReport: () -> Unit) {
    DashboardCard {
        Text(text = "InBody score", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        when (state) {
            CardState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shimmerPlaceholder(visible = true, shape = RoundedCornerShape(8.dp)),
            )
            is CardState.Error -> Text(
                text = "Couldn't load your latest stats",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CardState.Success -> {
                val record = state.data
                if (record == null) {
                    Text(
                        text = "No InBody data yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatColumn("Weight", record.weightKg?.let { "%.1f kg".format(Locale.US, it) } ?: "—")
                        StatColumn("BMI", record.bmi?.let { "%.1f".format(Locale.US, it) } ?: "—")
                        StatColumn("Body fat", record.bodyFatPct?.let { "%.1f%%".format(Locale.US, it) } ?: "—")
                    }
                }
            }
        }
        TextButton(onClick = onViewFullReport) {
            Text(text = "View full report →", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActiveProgrammeCard(programme: PatientProgrammeDto) {
    DashboardCard {
        Text(text = programme.catalogueItem?.name ?: "Programme", style = MaterialTheme.typography.titleLarge)
        programme.assignedDoctor?.let {
            Text(
                text = "with ${it.name}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        val (progress, daysLeft) = programmeProgress(programme.startDate, programme.endDate)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (daysLeft != null) "$daysLeft days remaining" else "Ongoing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Returns (0f..1f progress, daysLeft) — null daysLeft when dates are missing/unparseable. */
private fun programmeProgress(startDate: String?, endDate: String?): Pair<Float, Int?> {
    val start = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val end = endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    if (start == null || end == null || end.isBefore(start)) return 0f to null

    val today = LocalDate.now()
    val totalDays = daysBetween(start, end).coerceAtLeast(1)
    val elapsedDays = daysBetween(start, today).coerceIn(0, totalDays)
    val daysLeft = daysBetween(today, end).coerceAtLeast(0)
    return (elapsedDays.toFloat() / totalDays.toFloat()) to daysLeft
}

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    ChronoUnit.DAYS.between(from, to).toInt()

@Composable
private fun OutstandingBalanceCard(invoices: List<DocumentListItemDto>, onPayInvoice: (String) -> Unit) {
    val totalDue = invoices.sumOf { it.total }
    val firstInvoice = invoices.first()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Outstanding balance",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "₹%.2f due".format(Locale.US, totalDue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { onPayInvoice(firstInvoice.id) }) {
                Text("Pay")
            }
        }
    }
}

/**
 * The next three enabled reminders, soonest first, from AN-04's Room-backed
 * reminder store. Reuses AN-04's own ReminderViewModel rather than re-reading the
 * DAO here — Room is an app-wide singleton underneath, so a second Hilt-scoped
 * instance costs nothing and keeps one source of truth for reminder state.
 */
@Composable
private fun UpcomingRemindersSection(viewModel: ReminderViewModel = hiltViewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val upcoming = remember(reminders) {
        reminders.filter { it.enabled }
            .mapNotNull { reminder ->
                nextOccurrenceOf(reminder.timeOfDay, reminder.daysOfWeek)?.let { reminder to it }
            }
            .sortedBy { it.second }
            .take(3)
    }

    DashboardCard {
        Text(text = "Upcoming reminders", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        if (upcoming.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "No reminders yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcoming.forEach { (reminder, _) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(text = reminder.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = nextOccurrenceLabel(reminder.timeOfDay, reminder.daysOfWeek),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
