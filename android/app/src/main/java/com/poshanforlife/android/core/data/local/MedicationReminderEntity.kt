package com.poshanforlife.android.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-configured medication reminder. timeOfDay is "HH:mm" (24h, local
 * time). daysOfWeek is a comma-joined list of java.time.DayOfWeek names
 * (e.g. "MONDAY,WEDNESDAY,FRIDAY") — kept as a plain string rather than a
 * Room TypeConverter-backed collection since it's small and never queried on.
 */
@Entity(tableName = "medication_reminders")
data class MedicationReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val timeOfDay: String,
    val daysOfWeek: String,
    val enabled: Boolean = true,
)
