package com.poshanforlife.api.dto;

import java.util.List;

/**
 * GET /api/v1/dashboard/stats — a single role-aware aggregation. attentionPatients
 * is populated for DOCTOR callers only (null for ADMIN); assignmentOverview
 * is populated for ADMIN callers only (null for DOCTOR).
 */
public record DashboardStatsDto(
        DashboardKpisDto kpis,
        List<PbfTrendPointDto> pbfSixMonthTrend,
        List<BmiBucketDto> bmiDistribution,
        List<BodyCompositionPointDto> bodyCompositionScatter,
        List<DashboardActivityDto> recentActivity,
        List<AttentionPatientDto> attentionPatients,
        List<DoctorPatientCountDto> assignmentOverview) {
}
