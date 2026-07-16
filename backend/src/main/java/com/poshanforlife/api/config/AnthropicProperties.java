package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Anthropic API credentials — consumed by the (future) OCR/report-analysis service. */
@ConfigurationProperties(prefix = "app.anthropic")
public record AnthropicProperties(String apiKey, String model) {
}
