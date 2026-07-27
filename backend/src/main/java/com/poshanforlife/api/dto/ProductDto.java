package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductDto(
        String id,
        String segmentId,
        String segmentName,
        String name,
        String description,
        List<String> images,
        BigDecimal priceInr,
        String sku,
        ProductStatus status,
        int displayOrder,
        UserRefDto createdBy,
        Instant createdAt) {
}
