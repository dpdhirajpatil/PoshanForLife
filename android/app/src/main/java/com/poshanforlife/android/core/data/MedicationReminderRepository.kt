package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.data.local.MedicationReminderDao
import com.poshanforlife.android.core.data.local.MedicationReminderEntity
import com.poshanforlife.android.core.reminder.MedicationReminderScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** CRUD on Room + keeps each reminder's WorkManager job in sync — nothing here ever touches the network (see HealthTrackingRepository's kdoc). */
@Singleton
class MedicationReminderRepository @Inject constructor(
    private val dao: MedicationReminderDao,
    private val scheduler: MedicationReminderScheduler,
) {

    fun observeAll(): Flow<List<MedicationReminderEntity>> = dao.observeAll()

    suspend fun add(label: String, timeOfDay: String, daysOfWeek: String) {
        val id = dao.insert(MedicationReminderEntity(label = label, timeOfDay = timeOfDay, daysOfWeek = daysOfWeek))
        scheduler.schedule(dao.getById(id) ?: return)
    }

    suspend fun setEnabled(reminder: MedicationReminderEntity, enabled: Boolean) {
        val updated = reminder.copy(enabled = enabled)
        dao.update(updated)
        scheduler.schedule(updated)
    }

    suspend fun delete(reminder: MedicationReminderEntity) {
        scheduler.cancel(reminder.id)
        dao.delete(reminder)
    }

    /** Re-arms every enabled reminder's WorkManager job — call once at app/process start (jobs don't survive a WorkManager DB wipe, e.g. after an app update). */
    suspend fun rescheduleAllEnabled() {
        dao.getAllEnabled().forEach(scheduler::schedule)
    }
}
