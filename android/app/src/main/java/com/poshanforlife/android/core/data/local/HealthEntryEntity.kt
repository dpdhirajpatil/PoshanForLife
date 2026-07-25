package com.poshanforlife.android.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Metric type constants stored in [HealthEntryEntity.type] — kept as plain strings, not an enum, per the entity's spec. */
object HealthMetricType {
    const val WATER = "water"
    const val NUTRITION = "nutrition"
    const val SLEEP = "sleep"
    const val WEIGHT = "weight"
}

/**
 * A single manually-logged health metric entry, cached locally first for
 * instant UI feedback. `synced` tracks whether it's been pushed to the
 * backend — see HealthTrackingRepository's kdoc for why that's currently
 * always false (no backend write endpoint exists yet for these metrics).
 */
@Entity(tableName = "health_entries")
data class HealthEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Float,
    val unit: String,
    val loggedAt: Long,
    val synced: Boolean = false,
)
