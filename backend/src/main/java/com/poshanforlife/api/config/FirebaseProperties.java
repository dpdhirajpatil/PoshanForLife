package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase service account credentials, consumed by {@link FirebaseConfig} to
 * initialize the Admin SDK for FCM push. Exactly one of credentialsPath /
 * credentialsBase64 is expected to be set outside local dev; both blank
 * disables push (see FirebaseConfig).
 */
@ConfigurationProperties(prefix = "app.firebase")
public record FirebaseProperties(String credentialsPath, String credentialsBase64) {
}
