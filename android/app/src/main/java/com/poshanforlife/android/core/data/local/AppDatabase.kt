package com.poshanforlife.android.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HealthEntryEntity::class, MedicationReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthEntryDao(): HealthEntryDao
    abstract fun medicationReminderDao(): MedicationReminderDao
}
