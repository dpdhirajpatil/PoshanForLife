package com.poshanforlife.api.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Sends FCM push notifications to the Android app. Fire-and-forget: called
 * from NotificationService.create after the in-app Notification row is
 * already persisted, so a push failure never affects the triggering request.
 */
@Slf4j
@Service
public class FcmPushService {

    @Async
    public void send(String fcmToken, String title, String body, String relatedEntityType, UUID relatedEntityId) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.debug("Firebase not initialized — skipping push (title={})", title);
            return;
        }
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putData("relatedEntityType", relatedEntityType == null ? "" : relatedEntityType)
                .putData("relatedEntityId", relatedEntityId == null ? "" : relatedEntityId.toString())
                .build();
        try {
            // Already running on the @Async executor thread, so a blocking call here
            // doesn't block the triggering request — send() (not sendAsync) is fine.
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.warn("FCM push failed for token {}", mask(fcmToken), e);
        }
    }

    private static String mask(String token) {
        return token.length() > 8 ? token.substring(0, 8) + "…" : "…";
    }
}
