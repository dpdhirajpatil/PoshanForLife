package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.PatientProgrammeStatus;

import java.time.LocalDate;

/** The assignment context nested in an order detail (invoice line item). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderProgrammeDto(
        String id,
        CatalogueItemType serviceType,
        ServiceRefDto catalogueItem,
        LocalDate startDate,
        LocalDate endDate,
        PatientProgrammeStatus status,
        UserRefDto assignedBy,
        UserRefDto assignedDoctor) {
}
