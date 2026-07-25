package com.poshanforlife.android.feature.patient.track

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.data.HealthTrackingRepository
import com.poshanforlife.android.core.data.local.HealthEntryEntity
import com.poshanforlife.android.core.data.local.HealthMetricType
import com.poshanforlife.android.core.datastore.Goals
import com.poshanforlife.android.core.datastore.GoalsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class TrackUiState(
    val goals: Goals = Goals(),
    val waterLoggedMl: Int = 0,
    val nutritionEntriesToday: List<HealthEntryEntity> = emptyList(),
    val sleepHoursToday: Float? = null,
    val latestWeightKg: Float? = null,
)

@HiltViewModel
class TrackViewModel @Inject constructor(
    private val repository: HealthTrackingRepository,
    private val goalsDataStore: GoalsDataStore,
) : ViewModel() {

    private val startOfToday: Long
        get() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val uiState: StateFlow<TrackUiState> = combine(
        goalsDataStore.goals,
        repository.observeSince(startOfToday),
        repository.observeLatestOfType(HealthMetricType.WEIGHT),
    ) { goals, todayEntries, latestWeight ->
        TrackUiState(
            goals = goals,
            waterLoggedMl = todayEntries
                .filter { it.type == HealthMetricType.WATER }
                .sumOf { it.value.toInt() },
            nutritionEntriesToday = todayEntries
                .filter { it.type == HealthMetricType.NUTRITION }
                .sortedByDescending { it.loggedAt },
            sleepHoursToday = todayEntries
                .filter { it.type == HealthMetricType.SLEEP }
                .maxByOrNull { it.loggedAt }?.value,
            latestWeightKg = latestWeight?.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiState())

    fun logWater(ml: Int) {
        viewModelScope.launch { repository.log(HealthMetricType.WATER, ml.toFloat(), "ml") }
    }

    fun logNutrition(mealType: String, calories: Int?) {
        viewModelScope.launch {
            repository.log(HealthMetricType.NUTRITION, (calories ?: 0).toFloat(), "kcal:$mealType")
        }
    }

    fun logSleep(bedTime: LocalTime, wakeTime: LocalTime) {
        val minutes = if (wakeTime.isAfter(bedTime)) {
            ChronoUnit.MINUTES.between(bedTime, wakeTime)
        } else {
            // Crossed midnight (e.g. bed 23:00, wake 07:00).
            ChronoUnit.MINUTES.between(bedTime, LocalTime.MAX) + ChronoUnit.MINUTES.between(LocalTime.MIN, wakeTime) + 1
        }
        val hours = minutes / 60f
        viewModelScope.launch { repository.log(HealthMetricType.SLEEP, hours, "hours") }
    }

    fun logWeight(kg: Float) {
        viewModelScope.launch { repository.log(HealthMetricType.WEIGHT, kg, "kg") }
    }
}
