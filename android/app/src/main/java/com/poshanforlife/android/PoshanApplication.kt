package com.poshanforlife.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.poshanforlife.android.core.data.MedicationReminderRepository
import com.poshanforlife.android.core.di.ApplicationScope
import com.poshanforlife.android.core.fcm.createUpdatesNotificationChannel
import com.poshanforlife.android.core.healthconnect.HealthConnectManager
import com.poshanforlife.android.core.healthconnect.HealthConnectSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PoshanApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderRepository: MedicationReminderRepository

    @Inject
    lateinit var healthConnectManager: HealthConnectManager

    @Inject
    lateinit var healthConnectSyncScheduler: HealthConnectSyncScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createUpdatesNotificationChannel(this)
        // Defensive: WorkManager jobs already survive process death on their own; this
        // just re-arms enabled reminders if WorkManager's own store was ever cleared.
        applicationScope.launch { reminderRepository.rescheduleAllEnabled() }
        // Same defensive re-arm for the AN-11 Health Connect sync, but only if the user
        // already granted permissions in a previous session (enqueueUniquePeriodicWork's
        // KEEP policy makes this a no-op if it's already scheduled).
        applicationScope.launch {
            if (healthConnectManager.hasAllPermissions()) {
                healthConnectSyncScheduler.schedulePeriodic()
            }
        }
    }
}
