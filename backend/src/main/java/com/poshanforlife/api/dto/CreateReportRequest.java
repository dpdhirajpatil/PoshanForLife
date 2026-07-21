package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Manual report record (no file) — for LAB/PRESCRIPTION/OTHER types. */
public record CreateReportRequest(
        @NotNull UUID patientId,
        @NotNull @Size(min = 1, max = 255) String title,
        @NotNull ReportType type,
        @Size(max = 5000) String notes) {
}
