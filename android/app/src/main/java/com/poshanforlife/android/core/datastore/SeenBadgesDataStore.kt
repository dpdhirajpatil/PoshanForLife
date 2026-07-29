package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.seenBadgesDataStore by preferencesDataStore(name = "poshan_seen_badges")

/**
 * Tracks which earned badge ids the celebration animation has already been
 * shown for, so BadgesScreen only confetti-bursts once per newly-earned
 * badge rather than on every screen visit — there is no "newly earned" flag
 * in the API response itself.
 */
@Singleton
class SeenBadgesDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val SEEN_BADGE_IDS = stringSetPreferencesKey("seen_badge_ids")
    }

    val seenBadgeIds: Flow<Set<String>> = context.seenBadgesDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[Keys.SEEN_BADGE_IDS] ?: emptySet() }

    suspend fun markSeen(badgeId: String) {
        val current = seenBadgeIds.first()
        context.seenBadgesDataStore.edit { prefs ->
            prefs[Keys.SEEN_BADGE_IDS] = current + badgeId
        }
    }
}
