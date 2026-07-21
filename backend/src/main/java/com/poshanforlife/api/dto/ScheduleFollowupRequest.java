package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ScheduleFollowupRequest(
        @NotNull OffsetDateTime followupAt,
        @Size(max = 1000) String message) {
}
