package com.poshanforlife.api.dto;

import java.math.BigDecimal;

/** month is "yyyy-MM"; avgBodyFatPct is null when no records exist that month. */
public record PbfTrendPointDto(String month, BigDecimal avgBodyFatPct) {
}
