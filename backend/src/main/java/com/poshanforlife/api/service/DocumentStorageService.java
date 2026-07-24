package com.poshanforlife.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poshanforlife.api.config.SupabaseProperties;
import com.poshanforlife.api.exception.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Generated estimate/invoice PDF uploads to Supabase Storage's PRIVATE
 * `poshan-documents` bucket — same private-bucket-plus-signed-URL shape as
 * {@link ReportStorageService}, but the bytes come from {@link
 * DocumentPdfRenderer} (server-generated) rather than a user upload, and the
 * signed URL lives much longer: report PDFs are opened once right after
 * upload, but a document's pdfUrl is handed to the mobile app so IT can build
 * a WhatsApp share link the recipient might not open for hours — a 15-minute
 * TTL would routinely be dead on arrival, so this uses 24 hours instead.
 */
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private static final Duration SIGNED_URL_TTL = Duration.ofHours(24);

    private final SupabaseProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Uploads the rendered PDF bytes; returns the bucket-relative object path (not a URL). */
    public String uploadPdf(byte[] pdfBytes, UUID documentId) {
        requireConfigured();
        String objectPath = documentId + ".pdf";
        try {
            restClient.post()
                    .uri(baseUrl() + "/object/" + properties.documentsBucket() + "/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new StorageException("Document PDF upload failed", e);
        }
        return objectPath;
    }

    /** Mints a fresh 24-hour signed URL for a stored object path. */
    public String createSignedUrl(String objectPath) {
        requireConfigured();
        try {
            String response = restClient.post()
                    .uri(baseUrl() + "/object/sign/" + properties.documentsBucket() + "/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", SIGNED_URL_TTL.toSeconds()))
                    .retrieve()
                    .body(String.class);
            String signedPath = objectMapper.readTree(response).get("signedURL").asText();
            return baseUrl() + signedPath;
        } catch (RestClientException | IOException e) {
            throw new StorageException("Could not generate a download link for this document", e);
        }
    }

    private void requireConfigured() {
        if (isBlank(properties.storageUrl()) || isBlank(properties.serviceKey())
                || isBlank(properties.documentsBucket())) {
            throw new StorageException("Document file storage is not configured on the server");
        }
    }

    private String baseUrl() {
        return properties.storageUrl().replaceAll("/+$", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
