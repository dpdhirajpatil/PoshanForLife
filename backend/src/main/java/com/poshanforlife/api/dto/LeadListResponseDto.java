package com.poshanforlife.api.dto;

import java.util.List;

/** The list endpoint's `data` payload: rows for the current page + the pipeline summary. */
public record LeadListResponseDto(List<LeadListItemDto> leads, LeadSummaryDto summary) {
}
