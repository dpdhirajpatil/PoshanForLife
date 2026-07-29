package com.poshanforlife.android.core.healthconnect

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/** Today's aggregates read from Health Connect — any field is null if that record type had no data (or wasn't granted). */
data class HealthConnectDailySnapshot(
    val steps: Long?,
    val sleepMinutes: Long?,
    val avgHeartRateBpm: Long?,
    val latestWeightKg: Double?,
)

/**
 * Wraps the Health Connect client + permission flow (AN-11). Read-only: this
 * app only ever consumes StepsRecord/SleepSessionRecord/HeartRateRecord/
 * WeightRecord, never writes them, so manual logs in the Track screen (AN-04)
 * do not round-trip back into Health Connect for other apps to see.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    /** False when the Health Connect app isn't installed, or the OS/provider needs an update. */
    fun isAvailable(): Boolean =
        HealthConnectClient.sdkStatus(context, HEALTH_CONNECT_PACKAGE) == HealthConnectClient.SDK_AVAILABLE

    private fun client(): HealthConnectClient? = if (isAvailable()) HealthConnectClient.getOrCreate(context) else null

    fun permissionRequestContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun hasAllPermissions(): Boolean {
        val granted = client()?.permissionController?.getGrantedPermissions() ?: return false
        return granted.containsAll(permissions)
    }

    /** Null when Health Connect isn't available or permissions aren't (fully) granted. */
    suspend fun readToday(): HealthConnectDailySnapshot? {
        val client = client() ?: return null
        if (!hasAllPermissions()) return null

        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(LocalDate.now().atStartOfDay(zone).toInstant(), Instant.now())

        val aggregate: AggregationResult = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL, SleepSessionRecord.SLEEP_DURATION_TOTAL, HeartRateRecord.BPM_AVG),
                timeRangeFilter = range,
            ),
        )
        val latestWeightKg = client.readRecords(
            ReadRecordsRequest(recordType = WeightRecord::class, timeRangeFilter = range, ascendingOrder = false, pageSize = 1),
        ).records.firstOrNull()?.weight?.inKilograms

        return HealthConnectDailySnapshot(
            steps = aggregate[StepsRecord.COUNT_TOTAL],
            sleepMinutes = aggregate[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes(),
            avgHeartRateBpm = aggregate[HeartRateRecord.BPM_AVG],
            latestWeightKg = latestWeightKg,
        )
    }

    /** Play Store listing — Health Connect is a separate app on most devices (built into the OS only on very recent Android). */
    fun playStoreIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))

    /** Deep link into the Health Connect app's own permissions UI — the only way to revoke, there is no in-app API for it. */
    fun healthConnectSettingsIntent(): Intent =
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
}
