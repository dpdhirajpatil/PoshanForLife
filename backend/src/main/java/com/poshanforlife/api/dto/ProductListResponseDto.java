package com.poshanforlife.api.dto;

import java.util.List;

/** Flat, not pre-grouped by segment — simpler to filter/search across segments; the frontend groups by segmentId client-side. */
public record ProductListResponseDto(List<ProductDto> products) {
}
