package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MSG91 transactional-SMS credentials, consumed by OtpSmsClient.
 *
 * <p>{@code templateId} and {@code senderId} both have to be registered and
 * approved through MSG91's DLT compliance flow before real sends work in
 * India — that's a business/paperwork step measured in days, not a code
 * change, so start it well before you need it. Until then, run the local
 * profile's fixed-OTP bypass (see PhoneOtpService).
 *
 * @param authKey    MSG91 auth key — a secret, injected from the environment
 * @param templateId DLT-approved OTP template id
 * @param senderId   DLT-approved 6-character sender id, e.g. POSHAN
 * @param baseUrl    overridable for tests/sandbox
 */
@ConfigurationProperties(prefix = "app.msg91")
public record Msg91Properties(String authKey, String templateId, String senderId, String baseUrl) {
}
