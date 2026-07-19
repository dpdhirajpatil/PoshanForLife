package com.poshanforlife.api.service;

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
import java.util.Map;
import java.util.UUID;

/**
 * Cover-image uploads to Supabase Storage over its REST API. Implemented once
 * and shared by all three catalogue types (folder = the type's path segment).
 * The service-role key never leaves the backend; clients only ever receive
 * the public object URL.
 */
@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            "image/webp", ".webp",
            MediaType.IMAGE_GIF_VALUE, ".gif");

    private final SupabaseProperties properties;
    private final RestClient restClient = RestClient.create();

    /** Uploads an image and returns its public URL. */
    public String uploadImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "An image file is required");
        }
        String extension = file.getContentType() == null
                ? null : IMAGE_EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only JPEG, PNG, WebP or GIF images are allowed");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Image must be 5 MB or smaller");
        }
        if (isBlank(properties.storageUrl()) || isBlank(properties.serviceKey())) {
            throw new StorageException("File storage is not configured on the server");
        }

        String baseUrl = properties.storageUrl().replaceAll("/+$", "");
        String objectPath = properties.bucket() + "/" + folder + "/" + UUID.randomUUID() + extension;
        try {
            restClient.post()
                    .uri(baseUrl + "/object/" + objectPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.serviceKey())
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException | RestClientException e) {
            throw new StorageException("Cover image upload failed", e);
        }
        return baseUrl + "/object/public/" + objectPath;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
