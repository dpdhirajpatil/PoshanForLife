package com.poshanforlife.api.service;

import com.poshanforlife.api.config.Msg91Properties;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Sends OTP SMS through MSG91's transactional API.
 *
 * <p>Unlike the fire-and-forget FCM push path, a failure here is surfaced to
 * the caller: if the SMS never left, the user has no code to type and needs to
 * be told to try again rather than being left staring at an OTP field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpSmsClient {

    private static final String DEFAULT_BASE_URL = "https://control.msg91.com/api/v5";

    private final Msg91Properties properties;
    private final RestClient restClient = RestClient.create();

    /** True when credentials are present; false means the local fixed-OTP bypass is the only way in. */
    public boolean isConfigured() {
        return notBlank(properties.authKey()) && notBlank(properties.templateId());
    }

    /**
     * @param phone E.164, e.g. +919876543210
     * @param otp   the raw 6-digit code — passed to MSG91 only, never persisted
     */
    public void sendOtp(String phone, String otp) {
        if (!isConfigured()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "SMS sending is not configured on the server");
        }

        String baseUrl = notBlank(properties.baseUrl())
                ? properties.baseUrl().replaceAll("/+$", "")
                : DEFAULT_BASE_URL;

        // MSG91 wants the number without the leading "+".
        String mobile = phone.startsWith("+") ? phone.substring(1) : phone;

        Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("template_id", properties.templateId());
        body.put("mobile", mobile);
        // The variable name has to match the placeholder in the DLT-approved
        // template; ##OTP## is MSG91's own convention for their OTP templates.
        body.put("OTP", otp);
        if (notBlank(properties.senderId())) {
            body.put("sender", properties.senderId());
        }

        try {
            restClient.post()
                    .uri(baseUrl + "/otp")
                    .header("authkey", properties.authKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Deliberately no phone number or OTP in the log line.
            log.error("MSG91 OTP send failed", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Couldn't send the verification code. Please try again.");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
