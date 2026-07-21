package com.poshanforlife.api.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * stageCounts/followupToday/newThisWeek/converted/conversionRate all reflect
 * the same search/practitionerId/source filters as the list — but never the
 * list's own stage filter, since a stage-by-stage breakdown with an active
 * stage filter applied would be meaningless (see LeadRepository#countStats).
 */
public record LeadSummaryDto(
        Map<String, Long> stageCounts,
        long followupToday,
        long newThisWeek,
        long converted,
        BigDecimal conversionRate) {
}
