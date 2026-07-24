package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase Storage credentials — consumed by SupabaseStorageService (public
 * `bucket`, cover images), ReportStorageService (private `reportsBucket`,
 * InBody/report PDFs, downloaded only via short-lived signed URLs), and
 * DocumentStorageService (private `documentsBucket`, generated estimate/
 * invoice PDFs, same signed-URL pattern but a longer TTL — see its javadoc).
 */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(
        String storageUrl, String serviceKey, String bucket, String reportsBucket, String documentsBucket) {
}
