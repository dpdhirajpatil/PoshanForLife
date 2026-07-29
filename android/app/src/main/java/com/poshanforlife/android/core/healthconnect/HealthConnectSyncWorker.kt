package com.poshanforlife.android.core.healthconnect

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.poshanforlife.android.core.data.HealthRecordRepository
import com.poshanforlife.android.core.data.HealthTrackingRepository
import com.poshanforlife.android.core.data.local.HealthMetricType
import com.poshanforlife.android.core.datastore.HealthConnectSyncStateDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

private const val WEARABLE_SYNC_SOURCE = "wearable_sync"

/**
 * Reads today's Health Connect aggregates and writes them into the local
 * Room cache (source=health_connect, see HealthEntrySource) so the Track
 * screen can show them distinctly from manual logs. Only weight is also
 * pushed to the backend — steps/sleep/heart-rate have no supporting column
 * on HealthRecord yet, so they stay local-only for now (same "documented gap"
 * pattern as HealthTrackingRepository's other unsynced metric types).
 */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val healthConnectManager: HealthConnectManager,
    private val healthTrackingRepository: HealthTrackingRepository,
    private val healthRecordRepository: HealthRecordRepository,
    private val syncStateDataStore: HealthConnectSyncStateDataStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val snapshot = try {
            healthConnectManager.readToday()
        } catch (e: Exception) {
            return Result.retry()
        } ?: return Result.success() // not available / not (fully) permitted — nothing to do, not an error

        snapshot.steps?.let {
            healthTrackingRepository.upsertFromHealthConnect(HealthMetricType.STEPS, it.toFloat(), "steps")
        }
        snapshot.sleepMinutes?.let {
            healthTrackingRepository.upsertFromHealthConnect(HealthMetricType.SLEEP, it / 60f, "hours")
        }
        snapshot.avgHeartRateBpm?.let {
            healthTrackingRepository.upsertFromHealthConnect(HealthMetricType.HEART_RATE, it.toFloat(), "bpm")
        }
        snapshot.latestWeightKg?.let { weightKg ->
            healthTrackingRepository.upsertFromHealthConnect(HealthMetricType.WEIGHT, weightKg.toFloat(), "kg")
            healthRecordRepository.upsert(source = WEARABLE_SYNC_SOURCE, weightKg = weightKg)
        }

        syncStateDataStore.markSynced()
        return Result.success()
    }
}
