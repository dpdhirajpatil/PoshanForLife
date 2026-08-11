package com.poshanforlife.android.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "poshan_theme")

/**
 * What the user picked in Appearance. [SYSTEM] is the default: an app that
 * ignores the device setting until told otherwise is the surprising one.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val label: String
        get() = when (this) {
            SYSTEM -> "System"
            LIGHT -> "Light"
            DARK -> "Dark"
        }

    val description: String
        get() = when (this) {
            SYSTEM -> "Follow the device setting"
            LIGHT -> "Always light"
            DARK -> "Always dark"
        }

    companion object {
        /** Unknown/corrupt stored values fall back to SYSTEM rather than throwing. */
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * App-wide appearance preference. Deliberately app-wide rather than per-role:
 * the three brand themes (Patient/Lead/Staff) each already have their own light
 * and dark palettes, and light-or-dark is a device-level user preference, not a
 * property of which role happens to be signed in.
 *
 * Written from the Appearance screen; read once at the top of the UI tree in
 * MainActivity and published through `LocalDarkTheme`.
 */
@Singleton
class ThemePreferenceDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> ThemeMode.fromStored(prefs[Keys.THEME_MODE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
    }
}
