package com.poshanforlife.android.core.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** Separate from ReminderNotifier's "medication_reminders" channel — this one is for backend-pushed updates. */
const val UPDATES_CHANNEL_ID = "poshan_updates"

/** Created once from PoshanApplication.onCreate() — required on API 26+ (always, since minSdk is 29). */
fun createUpdatesNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            UPDATES_CHANNEL_ID,
            "Updates",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "New reports, programme assignments, and appointment updates"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
