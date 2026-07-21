package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.InBodyData;
import com.poshanforlife.api.entity.ReportStatus;
import jakarta.validation.constraints.Size;

/**
 * parsedData lets the upload review screen persist user corrections to a
 * low-confidence AI extraction — when present, ReportService.update()
 * re-upserts the linked HealthRecord with the corrected values too.
 */
public record UpdateReportRequest(
        @Size(min = 1, max = 255) String title,
        @Size(max = 5000) String notes,
        ReportStatus status,
        InBodyData parsedData) {
}
