package com.poshanforlife.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poshanforlife.api.config.SupabaseProperties;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Report/InBody PDF uploads to Supabase Storage's PRIVATE `poshan-reports`
 * bucket. Unlike {@link SupabaseStorageService} (public cover images), this
 * never returns a public URL: {@link #uploadPdf} returns only the bucket-
 * relative object path, which {@link Report#getFileUrl()}-callers persist;
 * {@link #createSignedUrl} mints a short-lived download URL from that path
 * on demand, so nothing long-lived or unauthenticated ever points at patient
 * report PDFs.
 */
@Service
@RequiredArgsConstructor
public class ReportStorageService {

    static final long MAX_PDF_BYTES = 10 * 1024 * 1024;
    private static final Duration SIGNED_URL_TTL = Duration.ofMinutes(15);

    private final SupabaseProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Validates and uploads a report PDF; returns the bucket-relative object path (not a URL). */
    public String uploadPdf(MultipartFile file, UUID patientId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A PDF file is required");
        }
        if (!MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Only PDF files are allowed");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "File must be 10 MB or smaller");
        }
        requireConfigured();

        String objectPath = patientId + "/" + UUID.randomUUID() + ".pdf";
        try {
            restClient.post()
                    .uri(baseUrl() + "/object/" + properties.reportsBucket() + "/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            throw new StorageException("Report PDF upload failed", e);
        }
        return objectPath;
    }

    /** Mints a fresh short-lived signed URL for a stored object path. Returns null if path is null. */
    public String createSignedUrl(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return null;
        }
        requireConfigured();
        try {
            String response = restClient.post()
                    .uri(baseUrl() + "/object/sign/" + properties.reportsBucket() + "/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", SIGNED_URL_TTL.toSeconds()))
                    .retrieve()
                    .body(String.class);
            String signedPath = objectMapper.readTree(response).get("signedURL").asText();
            return baseUrl() + signedPath;
        } catch (RestClientException | IOException e) {
            throw new StorageException("Could not generate a download link for this report", e);
        }
    }

    /** Best-effort delete — used to clean up an uploaded PDF if extraction fails downstream. */
    public void delete(String objectPath) {
        if (objectPath == null || objectPath.isBlank()) {
            return;
        }
        try {
            restClient.delete()
                    .uri(baseUrl() + "/object/" + properties.reportsBucket() + "/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ignored) {
            // Best-effort cleanup only — the DB state (or lack thereof) is the source of truth.
        }
    }

    private void requireConfigured() {
        if (isBlank(properties.storageUrl()) || isBlank(properties.serviceKey())
                || isBlank(properties.reportsBucket())) {
            throw new StorageException("Report file storage is not configured on the server");
        }
    }

    private String baseUrl() {
        return properties.storageUrl().replaceAll("/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
