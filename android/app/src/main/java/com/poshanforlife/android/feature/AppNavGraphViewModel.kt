package com.poshanforlife.android.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poshanforlife.android.core.datastore.ConversionWelcomeDataStore
import com.poshanforlife.android.core.fcm.DeepLinkEvents
import com.poshanforlife.android.core.fcm.FcmTokenSynchronizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin ViewModel wrapper so AppNavGraph (a plain @Composable, not a nav
 * destination) can reach the plain-singleton DeepLinkEvents/
 * FcmTokenSynchronizer/ConversionWelcomeDataStore classes via hiltViewModel()
 * — Hilt field injection isn't available directly inside a @Composable function.
 */
@HiltViewModel
class AppNavGraphViewModel @Inject constructor(
    deepLinkEvents: DeepLinkEvents,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
    private val conversionWelcomeDataStore: ConversionWelcomeDataStore,
) : ViewModel() {

    val deepLinks: SharedFlow<com.poshanforlife.android.core.fcm.DeepLinkTarget> = deepLinkEvents.events

    /** Fire-and-forget — called once per LoggedIn transition (covers "next app open" retries). */
    fun syncFcmToken() {
        viewModelScope.launch { fcmTokenSynchronizer.sync() }
    }

    /** AN-22: whether this user has already seen the LEAD->PATIENT ConversionWelcomeScreen. */
    suspend fun hasSeenConversionWelcome(userId: String): Boolean = conversionWelcomeDataStore.hasSeen(userId)

    fun markConversionWelcomeSeen(userId: String) {
        viewModelScope.launch { conversionWelcomeDataStore.markSeen(userId) }
    }
}
