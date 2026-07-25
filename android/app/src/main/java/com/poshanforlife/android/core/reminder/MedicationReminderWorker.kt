package com.poshanforlife.android.core.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.poshanforlife.android.core.data.local.MedicationReminderDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Fires once (its own OneTimeWorkRequest), posts the notification, then reschedules itself for the next occurrence. */
@HiltWorker
class MedicationReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderDao: MedicationReminderDao,
    private val notifier: ReminderNotifier,
    private val scheduler: MedicationReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong(REMINDER_ID_KEY, -1L)
        if (reminderId < 0) return Result.failure()

        val reminder = reminderDao.getById(reminderId) ?: return Result.success()
        if (reminder.enabled) {
            notifier.notify(reminder.id, reminder.label)
        }
        // Always reschedule (if still enabled) so the reminder keeps recurring — see scheduler kdoc.
        scheduler.schedule(reminder)
        return Result.success()
    }
}
