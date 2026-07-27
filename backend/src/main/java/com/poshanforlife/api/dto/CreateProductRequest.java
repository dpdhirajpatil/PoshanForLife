package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.ProductStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** status defaults to draft when omitted; displayOrder defaults to "append to end" within the segment. */
public record CreateProductRequest(
        @NotNull UUID segmentId,
        @NotBlank @Size(min = 2, max = 255) String name,
        String description,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal priceInr,
        @Size(max = 64) String sku,
        ProductStatus status,
        Integer displayOrder) {
}
