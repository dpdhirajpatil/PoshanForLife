package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Supabase Storage credentials — consumed by the (future) file-storage service. */
@ConfigurationProperties(prefix = "app.supabase")
public record SupabaseProperties(String storageUrl, String serviceKey) {
}
