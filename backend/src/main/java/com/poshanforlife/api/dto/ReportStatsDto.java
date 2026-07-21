package com.poshanforlife.api.dto;

/** Summary over the CURRENT FILTER SET (not just the current page) — mirrors the transactions summary pattern. */
public record ReportStatsDto(
        long total,
        long pending,
        long processing,
        long done,
        long error,
        long thisMonth) {
}
