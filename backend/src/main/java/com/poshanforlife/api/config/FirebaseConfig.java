package com.poshanforlife.api.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Initializes the Firebase Admin SDK (once, at startup) so FcmPushService can
 * call FirebaseMessaging.getInstance(). Credentials come from either a file
 * path (app.firebase.credentials-path) or a base64-encoded env var
 * (app.firebase.credentials-base64) — neither is ever committed to git. If
 * both are blank (e.g. local dev with push not needed), initialization is
 * skipped and FcmPushService logs+no-ops instead of failing the request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseConfig {

    private final FirebaseProperties firebaseProperties;

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (InputStream credentials = loadCredentials()) {
            if (credentials == null) {
                log.warn("No Firebase credentials configured (app.firebase.credentials-path / "
                        + "credentials-base64 both blank) — FCM push notifications are disabled");
                return;
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            log.warn("Failed to initialize Firebase Admin SDK — FCM push notifications are disabled", e);
        }
    }

    private InputStream loadCredentials() throws IOException {
        if (firebaseProperties.credentialsBase64() != null && !firebaseProperties.credentialsBase64().isBlank()) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(firebaseProperties.credentialsBase64()));
        }
        if (firebaseProperties.credentialsPath() != null && !firebaseProperties.credentialsPath().isBlank()) {
            return new FileInputStream(firebaseProperties.credentialsPath());
        }
        return null;
    }
}
