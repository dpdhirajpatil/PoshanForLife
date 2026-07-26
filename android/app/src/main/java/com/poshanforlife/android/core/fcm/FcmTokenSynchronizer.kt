package com.poshanforlife.android.core.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.poshanforlife.android.core.data.UserRepository
import com.poshanforlife.android.core.datastore.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Best-effort push of the device's current FCM token to the backend. Called
 * once per app-open/login (see AppNavGraph's LaunchedEffect(authState)) as
 * the retry path for onNewToken failures — PoshanFirebaseMessagingService
 * itself only gets one attempt per token rotation, so this covers "the app
 * was offline when the token last changed."
 */
@Singleton
class FcmTokenSynchronizer @Inject constructor(
    private val userRepository: UserRepository,
    private val tokenDataStore: TokenDataStore,
) {
    suspend fun sync() {
        val userId = tokenDataStore.currentUser().first()?.id ?: return
        val token = fetchToken() ?: return
        userRepository.updateFcmToken(userId, token)
    }

    private suspend fun fetchToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }
}
