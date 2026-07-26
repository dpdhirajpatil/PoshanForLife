package com.poshanforlife.android.core.fcm

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.poshanforlife.android.MainActivity
import com.poshanforlife.android.R
import com.poshanforlife.android.core.data.UserRepository
import com.poshanforlife.android.core.datastore.TokenDataStore
import com.poshanforlife.android.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PoshanFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var tokenDataStore: TokenDataStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /** One attempt per rotation — a failure here is caught up by FcmTokenSynchronizer on next app open. */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        applicationScope.launch {
            val userId = tokenDataStore.currentUser().first()?.id ?: return@launch
            userRepository.updateFcmToken(userId, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["message"].orEmpty()
        val relatedEntityType = message.data["relatedEntityType"]?.takeIf { it.isNotBlank() }
        val relatedEntityId = message.data["relatedEntityId"]?.takeIf { it.isNotBlank() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_RELATED_ENTITY_TYPE, relatedEntityType)
            putExtra(MainActivity.EXTRA_RELATED_ENTITY_ID, relatedEntityId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, UPDATES_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
