package com.poshanforlife.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.datastore.ThemeMode
import com.poshanforlife.android.core.datastore.ThemePreferenceDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Appearance setting wherever it appears — its own screen for
 * Practitioner/Admin, an embedded card on Patient's and Lead's Profile.
 *
 * Deliberately tiny and role-agnostic: DataStore is an app-wide singleton
 * underneath, so several concurrent instances of this ViewModel all observe the
 * same value and stay in sync (same reasoning as LeadHomeScreen instantiating a
 * second TrackViewModel to read AN-04's state).
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val themePreference: ThemePreferenceDataStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreference.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM,
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreference.setThemeMode(mode) }
    }
}
