package com.poshanforlife.android.feature.patient.track

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.data.local.HealthEntryEntity
import com.poshanforlife.android.ui.components.TimePickerDialog
import java.time.LocalTime
import java.util.Locale

@Composable
fun TrackScreen(
    modifier: Modifier = Modifier,
    viewModel: TrackViewModel = hiltViewModel(),
    reminderViewModel: ReminderViewModel = hiltViewModel(),
    onConnectHealthConnect: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenGoals) { Text("Goals") }
            }
        }
        item { WaterCard(loggedMl = state.waterLoggedMl, goalMl = state.goals.waterGoalMl, onAdd = viewModel::logWater) }
        item { NutritionCard(entries = state.nutritionEntriesToday, onLog = viewModel::logNutrition) }
        item { SleepCard(sleepHours = state.sleepHoursToday, goalHours = state.goals.sleepGoalHours, onLog = viewModel::logSleep) }
        item { WeightCard(latestKg = state.latestWeightKg, goalKg = state.goals.weightGoalKg, onLog = viewModel::logWeight) }
        item { StepsCard(onConnectHealthConnect = onConnectHealthConnect) }
        item { RemindersCard(viewModel = reminderViewModel) }
    }
}

@Composable
private fun TrackCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun WaterCard(loggedMl: Int, goalMl: Int, onAdd: (Int) -> Unit) {
    TrackCard {
        Text(text = "Water", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val progress = if (goalMl > 0) (loggedMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(text = "$loggedMl / $goalMl ml", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Daily goal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAdd(250) }) { Text("+250ml") }
            OutlinedButton(onClick = { onAdd(500) }) { Text("+500ml") }
        }
    }
}

private val mealTypes = listOf("Breakfast", "Lunch", "Dinner", "Snack")

@Composable
private fun NutritionCard(entries: List<HealthEntryEntity>, onLog: (mealType: String, calories: Int?) -> Unit) {
    TrackCard {
        Text(text = "Nutrition", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        var calories by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = calories,
            onValueChange = { calories = it.filter(Char::isDigit) },
            label = { Text("Calories (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mealTypes.forEach { meal ->
                OutlinedButton(onClick = { onLog(meal, calories.toIntOrNull()); calories = "" }) {
                    Text(meal)
                }
            }
        }
        if (entries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            entries.forEach { entry ->
                val mealType = entry.unit.substringAfter("kcal:", missingDelimiterValue = entry.unit)
                val kcalText = if (entry.value > 0) "${entry.value.toInt()} kcal" else "logged"
                Text(
                    text = "$mealType — $kcalText",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SleepCard(sleepHours: Float?, goalHours: Float, onLog: (LocalTime, LocalTime) -> Unit) {
    TrackCard {
        Text(text = "Sleep", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        if (sleepHours != null) {
            Text(
                text = "%.1f h logged today (goal %.1f h)".format(Locale.US, sleepHours, goalHours),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        var bedTime by remember { mutableStateOf(LocalTime.of(23, 0)) }
        var wakeTime by remember { mutableStateOf(LocalTime.of(7, 0)) }
        var showBedPicker by remember { mutableStateOf(false) }
        var showWakePicker by remember { mutableStateOf(false) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showBedPicker = true }) { Text("Bed ${bedTime.format()}") }
            OutlinedButton(onClick = { showWakePicker = true }) { Text("Wake ${wakeTime.format()}") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { onLog(bedTime, wakeTime) }) { Text("Log sleep") }

        if (showBedPicker) {
            TimePickerDialog(initial = bedTime, onDismiss = { showBedPicker = false }) {
                bedTime = it
                showBedPicker = false
            }
        }
        if (showWakePicker) {
            TimePickerDialog(initial = wakeTime, onDismiss = { showWakePicker = false }) {
                wakeTime = it
                showWakePicker = false
            }
        }
    }
}

@Composable
private fun WeightCard(latestKg: Float?, goalKg: Float, onLog: (Float) -> Unit) {
    TrackCard {
        Text(text = "Weight", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        val subtitle = when {
            latestKg != null && goalKg > 0 -> "Latest: %.1f kg · Goal: %.1f kg".format(Locale.US, latestKg, goalKg)
            latestKg != null -> "Latest: %.1f kg".format(Locale.US, latestKg)
            else -> "No weight logged yet"
        }
        Text(text = subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        var input by rememberSaveable { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                input.toFloatOrNull()?.let(onLog)
                input = ""
            }) { Text("Log weight") }
        }
    }
}

@Composable
private fun StepsCard(onConnectHealthConnect: () -> Unit) {
    TrackCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = "Steps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        // Read-only once Health Connect (AN-11) is wired — no manual entry for steps.
        Text(
            text = "Not connected",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onConnectHealthConnect) { Text("Connect Health Connect") }
    }
}

private fun LocalTime.format(): String = "%02d:%02d".format(hour, minute)
