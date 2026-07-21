package com.poshanforlife.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Anthropic API credentials + extraction settings, consumed by
 * AnthropicExtractionService. model and promptTemplate are configuration
 * (application.yml / env), never hardcoded in the service.
 */
@ConfigurationProperties(prefix = "app.anthropic")
public record AnthropicProperties(String apiKey, String model, String promptTemplate) {
}
