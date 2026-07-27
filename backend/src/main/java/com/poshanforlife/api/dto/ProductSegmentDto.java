package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.SegmentStatus;

import java.time.Instant;

public record ProductSegmentDto(
        String id,
        String name,
        int displayOrder,
        SegmentStatus status,
        long publishedProductCount,
        Instant createdAt) {
}
