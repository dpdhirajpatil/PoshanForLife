package com.poshanforlife.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poshanforlife.api.dto.ApiErrorResponse;
import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the wire format of the shared envelope so it can't drift from the frontend contract. */
class ApiEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void successEnvelopeOmitsMetaWhenAbsent() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.ok(Map.of("k", "v")));
        assertThat(json).isEqualTo("{\"success\":true,\"data\":{\"k\":\"v\"}}");
    }

    @Test
    void successEnvelopeIncludesMetaWhenPaginated() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.ok(Map.of(), 42, 2, 10));
        assertThat(json).contains("\"meta\":{\"total\":42,\"page\":2,\"limit\":10}");
    }

    @Test
    void errorEnvelopeMatchesContract() throws Exception {
        String json = objectMapper.writeValueAsString(
                ApiErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND, "Patient not found"));
        assertThat(json).isEqualTo(
                "{\"success\":false,\"error\":\"Patient not found\",\"code\":\"RESOURCE_NOT_FOUND\"}");
    }
}
