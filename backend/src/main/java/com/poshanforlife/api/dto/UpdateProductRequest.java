package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.ProductStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Partial update — every field optional, only non-null ones are applied. */
public record UpdateProductRequest(
        UUID segmentId,
        @Size(min = 2, max = 255) String name,
        String description,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal priceInr,
        @Size(max = 64) String sku,
        ProductStatus status,
        Integer displayOrder) {
}
