package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.goalsDataStore by preferencesDataStore(name = "poshan_goals")

data class Goals(
    val stepGoal: Int = DEFAULT_STEP_GOAL,
    val waterGoalMl: Int = DEFAULT_WATER_GOAL_ML,
    val sleepGoalHours: Float = DEFAULT_SLEEP_GOAL_HOURS,
    /** 0 means "not set" — the weight card should prompt the user to set one rather than showing 0/0. */
    val weightGoalKg: Float = 0f,
) {
    companion object {
        const val DEFAULT_STEP_GOAL = 10_000
        const val DEFAULT_WATER_GOAL_ML = 2_000
        const val DEFAULT_SLEEP_GOAL_HOURS = 8f
    }
}

/** Read by the Track screen's progress rings; written from the Goals settings screen. */
@Singleton
class GoalsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val STEP_GOAL = intPreferencesKey("step_goal")
        val WATER_GOAL_ML = intPreferencesKey("water_goal_ml")
        val SLEEP_GOAL_HOURS = floatPreferencesKey("sleep_goal_hours")
        val WEIGHT_GOAL_KG = floatPreferencesKey("weight_goal_kg")
    }

    val goals: Flow<Goals> = context.goalsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            Goals(
                stepGoal = prefs[Keys.STEP_GOAL] ?: Goals.DEFAULT_STEP_GOAL,
                waterGoalMl = prefs[Keys.WATER_GOAL_ML] ?: Goals.DEFAULT_WATER_GOAL_ML,
                sleepGoalHours = prefs[Keys.SLEEP_GOAL_HOURS] ?: Goals.DEFAULT_SLEEP_GOAL_HOURS,
                weightGoalKg = prefs[Keys.WEIGHT_GOAL_KG] ?: 0f,
            )
        }

    suspend fun save(goals: Goals) {
        context.goalsDataStore.edit { prefs ->
            prefs[Keys.STEP_GOAL] = goals.stepGoal
            prefs[Keys.WATER_GOAL_ML] = goals.waterGoalMl
            prefs[Keys.SLEEP_GOAL_HOURS] = goals.sleepGoalHours
            prefs[Keys.WEIGHT_GOAL_KG] = goals.weightGoalKg
        }
    }
}
