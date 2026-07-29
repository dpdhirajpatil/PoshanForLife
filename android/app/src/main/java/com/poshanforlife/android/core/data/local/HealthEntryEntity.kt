package com.poshanforlife.android.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Metric type constants stored in [HealthEntryEntity.type] — kept as plain strings, not an enum, per the entity's spec. */
object HealthMetricType {
    const val WATER = "water"
    const val NUTRITION = "nutrition"
    const val SLEEP = "sleep"
    const val WEIGHT = "weight"
    /** AN-11: Health-Connect-only, no manual logging UI for these. */
    const val STEPS = "steps"
    const val HEART_RATE = "heart_rate"
}

/** Distinguishes manually-logged entries from ones written by the AN-11 Health Connect sync worker. */
object HealthEntrySource {
    const val MANUAL = "manual"
    const val HEALTH_CONNECT = "health_connect"
}

/**
 * A single health metric entry, cached locally first for instant UI feedback
 * — either typed in manually or written by the Health Connect sync worker
 * (AN-11), distinguished by [source]. `synced` tracks whether it's been
 * pushed to the backend — see HealthTrackingRepository's kdoc for why that's
 * currently always false for most metric types (no backend write endpoint
 * exists yet for water/nutrition/sleep/steps/heart-rate; only weight has one).
 */
@Entity(tableName = "health_entries")
data class HealthEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Float,
    val unit: String,
    val loggedAt: Long,
    val synced: Boolean = false,
    val source: String = HealthEntrySource.MANUAL,
)
