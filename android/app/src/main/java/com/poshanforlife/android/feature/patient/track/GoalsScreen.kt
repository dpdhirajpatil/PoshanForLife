package com.poshanforlife.android.feature.patient.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.poshanforlife.android.core.datastore.Goals

/** Targets read by the Track screen's progress rings/labels — see GoalsDataStore. */
@Composable
fun GoalsScreen(modifier: Modifier = Modifier, viewModel: GoalsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()

    var stepGoal by remember { mutableStateOf("") }
    var waterGoal by remember { mutableStateOf("") }
    var sleepGoal by remember { mutableStateOf("") }
    var weightGoal by remember { mutableStateOf("") }

    LaunchedEffect(goals) {
        stepGoal = goals.stepGoal.toString()
        waterGoal = goals.waterGoalMl.toString()
        sleepGoal = goals.sleepGoalHours.toString()
        weightGoal = if (goals.weightGoalKg > 0) goals.weightGoalKg.toString() else ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Goals", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = stepGoal,
            onValueChange = { stepGoal = it.filter(Char::isDigit) },
            label = { Text("Daily step goal") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = waterGoal,
            onValueChange = { waterGoal = it.filter(Char::isDigit) },
            label = { Text("Daily water goal (ml)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = sleepGoal,
            onValueChange = { sleepGoal = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Daily sleep goal (hours)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = weightGoal,
            onValueChange = { weightGoal = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Target weight (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                viewModel.save(
                    Goals(
                        stepGoal = stepGoal.toIntOrNull() ?: Goals.DEFAULT_STEP_GOAL,
                        waterGoalMl = waterGoal.toIntOrNull() ?: Goals.DEFAULT_WATER_GOAL_ML,
                        sleepGoalHours = sleepGoal.toFloatOrNull() ?: Goals.DEFAULT_SLEEP_GOAL_HOURS,
                        weightGoalKg = weightGoal.toFloatOrNull() ?: 0f,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save goals") }
    }
}
