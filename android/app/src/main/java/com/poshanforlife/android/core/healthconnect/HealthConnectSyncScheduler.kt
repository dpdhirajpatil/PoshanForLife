package com.poshanforlife.android.core.healthconnect

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PERIODIC_WORK_NAME = "health_connect_sync_periodic"
private const val MANUAL_WORK_NAME = "health_connect_sync_manual"
private const val SYNC_INTERVAL_HOURS = 4L

@Singleton
class HealthConnectSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    /** KEEP: re-arming on every app start must not reset an already-scheduled interval's phase. */
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /** The Profile screen's "Sync now" action. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>().build()
        workManager.enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
