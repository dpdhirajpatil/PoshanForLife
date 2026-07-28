package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotNull;

/** The only supported action today is logging today's check-in — checkedInToday must be true. */
public record CheckInRequest(@NotNull Boolean checkedInToday) {
}
