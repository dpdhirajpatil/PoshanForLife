package com.poshanforlife.android.core.reminder

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.poshanforlife.android.core.data.local.MedicationReminderEntity
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

const val REMINDER_ID_KEY = "reminder_id"
const val REMINDER_LABEL_KEY = "reminder_label"
private const val TIME_FORMAT = "HH:mm"

/**
 * WorkManager has no native "fire at this exact wall-clock time, on these
 * days" primitive (PeriodicWorkRequest's minimum interval is 15 min and
 * doesn't align to a time-of-day). The standard pattern — used here — is a
 * single OneTimeWorkRequest whose initial delay is computed to the next
 * matching occurrence; MedicationReminderWorker reschedules the next one
 * itself after firing. enqueueUniqueWork keyed per reminder id means
 * updating or disabling a reminder always replaces its one pending job
 * instead of accumulating duplicates.
 */
@Singleton
class MedicationReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun schedule(reminder: MedicationReminderEntity) {
        if (!reminder.enabled) {
            cancel(reminder.id)
            return
        }
        val delayMillis = nextOccurrenceOf(reminder.timeOfDay, reminder.daysOfWeek)
            ?.let { java.time.Duration.between(LocalDateTime.now(), it).toMillis() } ?: return
        val request = OneTimeWorkRequestBuilder<MedicationReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(REMINDER_ID_KEY, reminder.id)
                    .putString(REMINDER_LABEL_KEY, reminder.label)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork(uniqueWorkName(reminder.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(reminderId: Long) {
        workManager.cancelUniqueWork(uniqueWorkName(reminderId))
    }

    private fun uniqueWorkName(reminderId: Long) = "medication_reminder_$reminderId"

}

/**
 * When this reminder next fires, or null when it never will (unparseable time, or
 * no days selected). Shared by the scheduler's delay calculation and the dashboard's
 * "next 3 upcoming" ordering so both agree on what "next" means.
 */
fun nextOccurrenceOf(timeOfDay: String, daysOfWeek: String): LocalDateTime? {
    val time = runCatching { LocalTime.parse(timeOfDay, DateTimeFormatter.ofPattern(TIME_FORMAT)) }
        .getOrNull() ?: return null
    val allowedDays = daysOfWeek.split(",")
        .mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }
        .toSet()
    if (allowedDays.isEmpty()) return null

    val now = LocalDateTime.now()
    for (dayOffset in 0..7) {
        val candidateDate = now.toLocalDate().plusDays(dayOffset.toLong())
        if (candidateDate.dayOfWeek !in allowedDays) continue
        val candidate = LocalDateTime.of(candidateDate, time)
        if (candidate.isAfter(now)) return candidate
    }
    return null
}

/** For UI display — the actual scheduling math lives above. */
fun nextOccurrenceLabel(timeOfDay: String, daysOfWeek: String): String {
    val days = daysOfWeek.split(",").mapNotNull { runCatching { DayOfWeek.valueOf(it.trim()) }.getOrNull() }
    if (days.isEmpty()) return timeOfDay
    val dayLabels = days.sortedBy { it.value }.joinToString(", ") {
        it.name.take(3).lowercase().replaceFirstChar(Char::uppercase)
    }
    return "$timeOfDay · $dayLabels"
}
