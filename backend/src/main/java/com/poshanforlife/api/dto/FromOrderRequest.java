package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FromOrderRequest(@NotNull UUID orderId) {
}
