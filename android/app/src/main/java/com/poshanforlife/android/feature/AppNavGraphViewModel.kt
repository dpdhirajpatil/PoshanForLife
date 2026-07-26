package com.poshanforlife.android.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.fcm.DeepLinkEvents
import com.poshanforlife.android.core.fcm.FcmTokenSynchronizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin ViewModel wrapper so AppNavGraph (a plain @Composable, not a nav
 * destination) can reach the plain-singleton DeepLinkEvents/
 * FcmTokenSynchronizer classes via hiltViewModel() — Hilt field injection
 * isn't available directly inside a @Composable function.
 */
@HiltViewModel
class AppNavGraphViewModel @Inject constructor(
    deepLinkEvents: DeepLinkEvents,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
) : ViewModel() {

    val deepLinks: SharedFlow<com.poshanforlife.android.core.fcm.DeepLinkTarget> = deepLinkEvents.events

    /** Fire-and-forget — called once per LoggedIn transition (covers "next app open" retries). */
    fun syncFcmToken() {
        viewModelScope.launch { fcmTokenSynchronizer.sync() }
    }
}
