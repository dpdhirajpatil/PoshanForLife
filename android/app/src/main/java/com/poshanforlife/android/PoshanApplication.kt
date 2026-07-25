package com.poshanforlife.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.poshanforlife.android.core.data.MedicationReminderRepository
import com.poshanforlife.android.core.di.ApplicationScope
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
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Defensive: WorkManager jobs already survive process death on their own; this
        // just re-arms enabled reminders if WorkManager's own store was ever cleared.
        applicationScope.launch { reminderRepository.rescheduleAllEnabled() }
    }
}
