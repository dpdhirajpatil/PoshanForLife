package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.poshanforlife.android.core.network.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "poshan_tokens")

/**
 * Persists the JWT access/refresh pair via Jetpack DataStore (Preferences),
 * not SharedPreferences directly. DataStore's default (unencrypted, app-
 * sandboxed) storage is accepted here rather than EncryptedSharedPreferences
 * because the access token is short-lived (see backend app.jwt.access-expiry-ms,
 * 15 min default) — the extra at-rest encryption buys little for a token
 * that expires that fast, and DataStore avoids EncryptedSharedPreferences'
 * known Keystore-invalidation edge cases (e.g. after biometric enrollment
 * changes) that can otherwise strand a user's session unrecoverably.
 */
@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_ROLE = stringPreferencesKey("user_role")
    }

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = access
            prefs[Keys.REFRESH_TOKEN] = refresh
        }
    }

    fun accessToken(): Flow<String?> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[Keys.ACCESS_TOKEN] }

    fun refreshToken(): Flow<String?> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[Keys.REFRESH_TOKEN] }

    /** Cached alongside the tokens so AuthViewModel can derive role without a network round trip on cold start. */
    suspend fun saveUser(user: UserDto) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_NAME] = user.name
            prefs[Keys.USER_EMAIL] = user.email
            prefs[Keys.USER_ROLE] = user.role
        }
    }

    /** Null once logged out (or before the first login) — the single source of truth AuthViewModel observes. */
    fun currentUser(): Flow<UserDto?> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val id = prefs[Keys.USER_ID] ?: return@map null
            UserDto(
                id = id,
                name = prefs[Keys.USER_NAME].orEmpty(),
                email = prefs[Keys.USER_EMAIL].orEmpty(),
                role = prefs[Keys.USER_ROLE].orEmpty(),
            )
        }

    /** Wipes tokens + cached user together — logout and failed-refresh both call this. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
