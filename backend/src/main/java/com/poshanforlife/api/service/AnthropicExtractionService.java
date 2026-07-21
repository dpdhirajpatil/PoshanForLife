package com.poshanforlife.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poshanforlife.api.config.AnthropicProperties;
import com.poshanforlife.api.entity.InBodyData;
import com.poshanforlife.api.exception.OcrExtractionException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Sends extracted PDF content (text, or a rendered page image for scanned
 * PDFs) to the Anthropic Messages API and parses the structured-JSON reply
 * into {@link InBodyData}. Model name and prompt come from {@link
 * AnthropicProperties} — nothing here is hardcoded per environment.
 */
@Service
@RequiredArgsConstructor
public class AnthropicExtractionService {

    /** >= 60% of the 20 fields populated is "high" confidence — otherwise "low". */
    private static final double HIGH_CONFIDENCE_RATIO = 0.6;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final AnthropicProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Result extract(PdfTextExtractionService.Extraction extraction) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new OcrExtractionException(
                    "AI extraction is not configured on the server (missing Anthropic API key)");
        }

        Object contentBlock = "image".equals(extraction.method())
                ? Map.of("type", "image", "source", Map.of(
                        "type", "base64", "media_type", "image/png", "data", extraction.base64PngPage()))
                : Map.of("type", "text", "text", "Report content:\n\n" + extraction.text());

        Map<String, Object> requestBody = Map.of(
                "model", properties.model(),
                "max_tokens", 1024,
                "system", properties.promptTemplate(),
                "messages", List.of(Map.of("role", "user", "content", List.of(contentBlock))));

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(API_URL)
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new OcrExtractionException("The AI extraction service could not be reached", e);
        }

        String jsonText = extractResponseText(responseBody);
        InBodyData parsed = parseInBodyJson(jsonText);
        int extractedFieldCount = parsed.extractedFieldCount();
        String confidence = extractedFieldCount >= HIGH_CONFIDENCE_RATIO * InBodyData.totalFieldCount()
                ? "high" : "low";
        return new Result(parsed, confidence, extractedFieldCount);
    }

    private String extractResponseText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                throw new OcrExtractionException("The AI extraction response was empty");
            }
            return content.get(0).path("text").asText();
        } catch (OcrExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new OcrExtractionException("Could not read the AI extraction response", e);
        }
    }

    private InBodyData parseInBodyJson(String jsonText) {
        String cleaned = jsonText.strip()
                .replaceAll("^```(json)?", "")
                .replaceAll("```$", "")
                .strip();
        try {
            return objectMapper.readValue(cleaned, InBodyData.class);
        } catch (Exception e) {
            throw new OcrExtractionException(
                    "The AI could not extract structured data from this report", e);
        }
    }

    public record Result(InBodyData parsedData, String confidence, int extractedFieldCount) {
    }
}
