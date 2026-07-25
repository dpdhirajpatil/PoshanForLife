package com.poshanforlife.android.core.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationReminderDao {
    @Insert
    suspend fun insert(reminder: MedicationReminderEntity): Long

    @Update
    suspend fun update(reminder: MedicationReminderEntity)

    @Delete
    suspend fun delete(reminder: MedicationReminderEntity)

    @Query("SELECT * FROM medication_reminders ORDER BY timeOfDay ASC")
    fun observeAll(): Flow<List<MedicationReminderEntity>>

    @Query("SELECT * FROM medication_reminders WHERE id = :id")
    suspend fun getById(id: Long): MedicationReminderEntity?

    @Query("SELECT * FROM medication_reminders WHERE enabled = 1")
    suspend fun getAllEnabled(): List<MedicationReminderEntity>
}
