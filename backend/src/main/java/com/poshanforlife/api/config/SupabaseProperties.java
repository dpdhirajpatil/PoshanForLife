package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Supabase Storage credentials — consumed by SupabaseStorageService (public
 * `bucket`, cover images) and ReportStorageService (private `reportsBucket`,
 * InBody/report PDFs, downloaded only via short-lived signed URLs).
 */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String storageUrl, String serviceKey, String bucket, String reportsBucket) {
}
