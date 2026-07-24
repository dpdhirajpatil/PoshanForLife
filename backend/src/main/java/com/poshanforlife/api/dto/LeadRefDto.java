package com.poshanforlife.api.dto;

/** Lead bill-to reference — enough for document rendering, mirrors PatientRefDto. */
public record LeadRefDto(String id, String name, String email, String phone) {
}
