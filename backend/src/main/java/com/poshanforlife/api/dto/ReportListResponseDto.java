package com.poshanforlife.api.dto;

import java.util.List;

/** The list endpoint's `data` payload: rows for the current page + stats over the whole filtered set. */
public record ReportListResponseDto(List<ReportListItemDto> reports, ReportStatsDto stats) {
}
