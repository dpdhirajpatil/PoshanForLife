package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void healthReturnsUpStatusEnvelope() {
        ApiResponse<Map<String, String>> response = controller.health();

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsEntry("status", "up");
        assertThat(response.meta()).isNull();
    }
}
