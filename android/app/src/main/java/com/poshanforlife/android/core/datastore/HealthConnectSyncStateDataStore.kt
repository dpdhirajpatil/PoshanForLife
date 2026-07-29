package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.healthConnectSyncDataStore by preferencesDataStore(name = "poshan_health_connect_sync")

/** Read by the Profile screen's "Connect Health Connect" row; written by the sync worker after every successful run. */
@Singleton
class HealthConnectSyncStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    }

    /** Null until the first successful sync. */
    val lastSyncedAtMillis: Flow<Long?> = context.healthConnectSyncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[Keys.LAST_SYNCED_AT] }

    suspend fun markSynced(atMillis: Long = System.currentTimeMillis()) {
        context.healthConnectSyncDataStore.edit { prefs -> prefs[Keys.LAST_SYNCED_AT] = atMillis }
    }

    suspend fun clear() {
        context.healthConnectSyncDataStore.edit { it.remove(Keys.LAST_SYNCED_AT) }
    }
}
