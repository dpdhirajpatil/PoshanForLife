package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.data.local.HealthEntryDao
import com.poshanforlife.android.core.data.local.HealthEntryEntity
import com.poshanforlife.android.core.data.local.HealthEntrySource
import com.poshanforlife.android.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first: every log() call writes to Room immediately (instant UI via
 * the DAO's Flow queries) and returns without waiting on the network.
 *
 * syncPending() is intentionally a no-op stub. As of this writing the
 * backend has no write endpoint for manually-logged health metrics at all —
 * POST /api/v1/reports/upload is the only path that writes a HealthRecord,
 * it's PDF/OCR-only, @AdminOrDoctor-gated, and has no field for
 * water/nutrition/sleep (those metrics have no backend table to sync to in
 * the first place). Entries stay synced=false until a real endpoint exists;
 * this method is the single place that will change when it does.
 */
@Singleton
class HealthTrackingRepository @Inject constructor(
    private val healthEntryDao: HealthEntryDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    suspend fun log(type: String, value: Float, unit: String, loggedAt: Long = System.currentTimeMillis()) {
        healthEntryDao.insert(HealthEntryEntity(type = type, value = value, unit = unit, loggedAt = loggedAt))
        applicationScope.launch { syncPending() }
    }

    fun observeSince(sinceEpochMillis: Long): Flow<List<HealthEntryEntity>> =
        healthEntryDao.observeSince(sinceEpochMillis)

    fun observeLatestOfType(type: String): Flow<HealthEntryEntity?> =
        healthEntryDao.observeLatestOfType(type)

    /**
     * Called by the Health Connect sync worker (AN-11) instead of [log]: replaces
     * today's Health-Connect-sourced value for this metric type in place rather than
     * inserting a new row every sync run (the worker re-reads today's full-day
     * aggregate each time, so the latest write is always the correct total).
     */
    suspend fun upsertFromHealthConnect(type: String, value: Float, unit: String, loggedAt: Long = System.currentTimeMillis()) {
        val startOfToday = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val existing = healthEntryDao.findLatestOfTypeAndSource(type, HealthEntrySource.HEALTH_CONNECT, startOfToday)
        if (existing != null) {
            healthEntryDao.update(existing.copy(value = value, unit = unit, loggedAt = loggedAt))
        } else {
            healthEntryDao.insert(
                HealthEntryEntity(type = type, value = value, unit = unit, loggedAt = loggedAt, source = HealthEntrySource.HEALTH_CONNECT),
            )
        }
    }

    private suspend fun syncPending() {
        val unsynced = healthEntryDao.getUnsynced()
        if (unsynced.isEmpty()) return
        // No-op until the backend exposes a write endpoint — see kdoc above.
    }
}
