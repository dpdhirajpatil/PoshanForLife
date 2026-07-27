package com.poshanforlife.api.dto;

/** {@code upserted} is true when this call updated an existing same-day record rather than creating a new one. */
public record UpsertHealthRecordResponseDto(HealthRecordDto record, boolean upserted) {
}
