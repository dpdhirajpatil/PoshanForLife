package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.InBodyData;
import com.poshanforlife.api.entity.ReportStatus;
import com.poshanforlife.api.entity.ReportType;

import java.time.Instant;

public record ReportDetailDto(
        String id,
        PatientRefDto patient,
        String title,
        ReportType type,
        String notes,
        ReportStatus status,
        /** Freshly-minted signed URL (private bucket) — null if this report has no file. */
        String fileUrl,
        InBodyData parsedData,
        String confidence,
        Integer extractedFieldCount,
        String extractionMethod,
        UserRefDto createdBy,
        Instant createdAt) {
}
