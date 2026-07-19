package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Minimal reference to the catalogue item behind an assignment/order —
 * includes the type-specific duration so invoices can render the line item
 * ("service name + duration"). Null-able as a whole: an archived catalogue
 * item may have been deleted after assignment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceRefDto(
        String id,
        String name,
        String serviceCode,
        Integer durationWeeks,
        Integer durationMinutes,
        Integer durationDays) {
}
