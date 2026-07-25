package com.poshanforlife.android.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthEntryDao {
    @Insert
    suspend fun insert(entry: HealthEntryEntity): Long

    @Update
    suspend fun update(entry: HealthEntryEntity)

    @Query("SELECT * FROM health_entries WHERE loggedAt >= :sinceEpochMillis ORDER BY loggedAt DESC")
    fun observeSince(sinceEpochMillis: Long): Flow<List<HealthEntryEntity>>

    @Query("SELECT * FROM health_entries WHERE type = :type ORDER BY loggedAt DESC LIMIT 1")
    fun observeLatestOfType(type: String): Flow<HealthEntryEntity?>

    @Query("SELECT * FROM health_entries WHERE synced = 0")
    suspend fun getUnsynced(): List<HealthEntryEntity>
}
