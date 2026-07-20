package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The order nested in a transaction detail — just enough to link through and render the invoice. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionOrderDto(String id, OrderProgrammeDto patientProgramme) {
}
