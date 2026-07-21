package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.ReportStatus;
import com.poshanforlife.api.entity.ReportType;

import java.time.Instant;

public record ReportListItemDto(
        String id,
        PatientRefDto patient,
        String title,
        ReportType type,
        ReportStatus status,
        Instant createdAt,
        UserRefDto createdBy) {
}
