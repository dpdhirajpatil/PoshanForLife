package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.Challenge;
import com.poshanforlife.api.entity.Programme;
import com.poshanforlife.api.entity.Session;

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

    /** Shared by the assignments, orders and transactions features. */
    public static ServiceRefDto of(CatalogueItem item) {
        return new ServiceRefDto(
                item.getId().toString(),
                item.getName(),
                item.getServiceCode(),
                item instanceof Programme p ? p.getDurationWeeks() : null,
                item instanceof Session s ? s.getDurationMinutes() : null,
                item instanceof Challenge c ? c.getDurationDays() : null);
    }
}
