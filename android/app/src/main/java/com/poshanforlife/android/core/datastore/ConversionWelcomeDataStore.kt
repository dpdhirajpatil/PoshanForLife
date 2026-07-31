package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.conversionWelcomeDataStore by preferencesDataStore(name = "poshan_conversion_welcome")

/**
 * AN-22: tracks which user ids have already seen the LEAD->PATIENT
 * ConversionWelcomeScreen, keyed by user id (not wiped on logout) so a later
 * re-login by the same now-permanently-PATIENT user never re-shows it —
 * same shape as SeenBadgesDataStore's own separate, un-cleared store.
 */
@Singleton
class ConversionWelcomeDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val SEEN_USER_IDS = stringSetPreferencesKey("seen_conversion_welcome_user_ids")
    }

    suspend fun hasSeen(userId: String): Boolean {
        val seenIds = context.conversionWelcomeDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs -> prefs[Keys.SEEN_USER_IDS] ?: emptySet() }
            .first()
        return userId in seenIds
    }

    suspend fun markSeen(userId: String) {
        context.conversionWelcomeDataStore.edit { prefs ->
            prefs[Keys.SEEN_USER_IDS] = (prefs[Keys.SEEN_USER_IDS] ?: emptySet()) + userId
        }
    }
}
