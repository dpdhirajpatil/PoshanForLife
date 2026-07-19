package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Supabase Storage credentials — consumed by SupabaseStorageService. */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String storageUrl, String serviceKey, String bucket) {
}
