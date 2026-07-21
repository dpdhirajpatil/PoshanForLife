package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.InBodyData;

import java.util.List;

public record ReportUploadResponseDto(
        String reportId,
        String healthRecordId,
        InBodyData parsedData,
        String fileUrl,
        String confidence,
        int extractedFieldCount,
        String extractionMethod,
        List<String> warnings) {
}
