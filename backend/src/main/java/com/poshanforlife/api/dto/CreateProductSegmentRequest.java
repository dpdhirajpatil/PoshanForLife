package com.poshanforlife.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** displayOrder defaults to "append to end" (max + 1) when omitted. */
public record CreateProductSegmentRequest(
        @NotBlank @Size(min = 2, max = 255) String name,
        Integer displayOrder) {
}
