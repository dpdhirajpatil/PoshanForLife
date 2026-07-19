package com.poshanforlife.api.dto;

/** Patient bill-to reference — enough for invoice rendering. */
public record PatientRefDto(String id, String name, String email, String phone) {
}
