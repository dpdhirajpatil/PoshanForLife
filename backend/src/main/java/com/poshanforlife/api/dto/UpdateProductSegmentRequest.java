package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.SegmentStatus;
import jakarta.validation.constraints.Size;

/** Partial update — every field optional, only non-null ones are applied. */
public record UpdateProductSegmentRequest(
        @Size(min = 2, max = 255) String name,
        Integer displayOrder,
        SegmentStatus status) {
}
