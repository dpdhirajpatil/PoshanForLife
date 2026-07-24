package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDocumentItemRequest(
        @NotBlank @Size(max = 255) String itemName,
        @Size(max = 2000) String description,
        @Size(max = 32) String hsnSac,
        @Positive int quantity,
        @Positive BigDecimal rateInr) {
}
