package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AttentionPatientDto;
import com.poshanforlife.api.dto.BmiBucketDto;
import com.poshanforlife.api.dto.BodyCompositionPointDto;
import com.poshanforlife.api.dto.DashboardActivityDto;
import com.poshanforlife.api.dto.DashboardKpisDto;
import com.poshanforlife.api.dto.DashboardStatsDto;
import com.poshanforlife.api.dto.DoctorPatientCountDto;
import com.poshanforlife.api.dto.PatientStatsDto;
import com.poshanforlife.api.dto.PbfTrendPointDto;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.LeadActivity;
import com.poshanforlife.api.entity.LeadActivityType;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Report;
import com.poshanforlife.api.entity.ReportStatus;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.LeadActivityRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.ReportRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GET /api/v1/dashboard/stats — one aggregation endpoint, role-aware
 * throughout: ADMIN sees org-wide numbers plus the per-doctor assignment
 * overview, DOCTOR sees numbers scoped to their own assigned patients/leads
 * plus their attention-patients list instead. Each aggregation is its own
 * method (package-private for direct unit testing) so they stay independently
 * testable and easy to swap for a cached read later (e.g. a short-TTL
 * Caffeine cache in front of {@link #getStats} — not needed yet at this data
 * volume, so left as a future optimization rather than added speculatively).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 20;
    private static final int ATTENTION_STALE_DAYS = 30;

    private final PatientService patientService;
    private final UserRepository userRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final ReportRepository reportRepository;
    private final LeadRepository leadRepository;
    private final LeadActivityRepository leadActivityRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getStats(AuthenticatedUser caller) {
        boolean isDoctor = caller.role() == Role.DOCTOR;
        UUID doctorId = isDoctor ? UUID.fromString(caller.id()) : null;
        Set<UUID> visiblePatientIds = isDoctor
                ? Set.copyOf(userRepository.findPatientIdsOfDoctor(doctorId))
                : null;

        List<HealthRecord> latestPerPatient = healthRecordRepository.findLatestPerPatient().stream()
                .filter(hr -> visiblePatientIds == null || visiblePatientIds.contains(hr.getPatient().getId()))
                .toList();

        return new DashboardStatsDto(
                computeKpis(caller, doctorId, isDoctor),
                computePbfTrend(visiblePatientIds),
                computeBmiDistribution(latestPerPatient),
                computeBodyCompositionScatter(latestPerPatient),
                computeRecentActivity(doctorId),
                isDoctor ? computeAttentionPatients(doctorId) : null,
                isDoctor ? null : computeAssignmentOverview());
    }

    DashboardKpisDto computeKpis(AuthenticatedUser caller, UUID doctorId, boolean isDoctor) {
        PatientStatsDto patientStats = patientService.stats(caller);
        long activeDoctors = userRepository.countByRoleAndIsActiveTrue(Role.DOCTOR);

        Instant epoch = Instant.EPOCH;
        Instant now = Instant.now();
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        ReportRepository.ReportStats reportStats = reportRepository.countStats(null, null, doctorId, null, "",
                epoch, now, monthStart, ReportStatus.PENDING, ReportStatus.PROCESSING, ReportStatus.DONE,
                ReportStatus.ERROR);
        long pendingReviews = nz(reportStats.getPending()) + nz(reportStats.getProcessing());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
        LeadRepository.LeadStats leadStats = leadRepository.countStats(doctorId, null, "",
                todayStart, todayEnd, weekStart, LeadStage.NEW, LeadStage.CONTACTED, LeadStage.QUALIFIED,
                LeadStage.PROPOSED, LeadStage.CONVERTED, LeadStage.LOST);

        return new DashboardKpisDto(
                patientStats.totalPatients(),
                activeDoctors,
                nz(reportStats.getThisMonth()),
                patientStats.averageBmi(),
                isDoctor ? patientStats.totalPatients() : null,
                pendingReviews,
                nz(leadStats.getFollowupToday()));
    }

    /** Average body-fat % per calendar month for the last 6 months, oldest first. Missing months average to null. */
    List<PbfTrendPointDto> computePbfTrend(Set<UUID> visiblePatientIds) {
        LocalDate rangeStart = LocalDate.now(ZoneOffset.UTC).minusMonths(5).withDayOfMonth(1);
        Map<YearMonth, List<BigDecimal>> byMonth = healthRecordRepository.findByRecordDateGreaterThanEqual(rangeStart)
                .stream()
                .filter(hr -> visiblePatientIds == null || visiblePatientIds.contains(hr.getPatient().getId()))
                .filter(hr -> hr.getBodyFatPct() != null)
                .collect(Collectors.groupingBy(hr -> YearMonth.from(hr.getRecordDate()),
                        Collectors.mapping(HealthRecord::getBodyFatPct, Collectors.toList())));

        List<PbfTrendPointDto> points = new ArrayList<>();
        YearMonth cursor = YearMonth.from(rangeStart);
        YearMonth end = YearMonth.now(ZoneOffset.UTC);
        while (!cursor.isAfter(end)) {
            points.add(new PbfTrendPointDto(cursor.toString(), average(byMonth.getOrDefault(cursor, List.of()))));
            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    /** Buckets each visible patient's latest BMI: Underweight &lt;18.5, Normal &lt;25, Overweight &lt;30, else Obese. */
    List<BmiBucketDto> computeBmiDistribution(List<HealthRecord> latestPerPatient) {
        long underweight = 0;
        long normal = 0;
        long overweight = 0;
        long obese = 0;
        for (HealthRecord record : latestPerPatient) {
            BigDecimal bmi = bmiOf(record);
            if (bmi == null) {
                continue;
            }
            double value = bmi.doubleValue();
            if (value < 18.5) {
                underweight++;
            } else if (value < 25) {
                normal++;
            } else if (value < 30) {
                overweight++;
            } else {
                obese++;
            }
        }
        return List.of(
                new BmiBucketDto("Underweight", underweight),
                new BmiBucketDto("Normal", normal),
                new BmiBucketDto("Overweight", overweight),
                new BmiBucketDto("Obese", obese));
    }

    List<BodyCompositionPointDto> computeBodyCompositionScatter(List<HealthRecord> latestPerPatient) {
        return latestPerPatient.stream()
                .filter(hr -> hr.getBodyFatPct() != null && hr.getSkeletalMuscleMassKg() != null)
                .map(hr -> new BodyCompositionPointDto(hr.getPatient().getId(), hr.getPatient().getName(),
                        hr.getBodyFatPct(), hr.getSkeletalMuscleMassKg()))
                .toList();
    }

    /** Merges new-patient/new-report/lead-stage-change events, newest first, capped at {@link #RECENT_ACTIVITY_LIMIT}. */
    List<DashboardActivityDto> computeRecentActivity(UUID doctorId) {
        Pageable topN = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);
        Pageable topNByCreatedDesc = PageRequest.of(0, RECENT_ACTIVITY_LIMIT, Sort.by("createdAt").descending());

        List<User> recentPatients = doctorId == null
                ? userRepository.search(Role.PATIENT, "", topNByCreatedDesc).getContent()
                : userRepository.searchPatientsOfDoctor(doctorId, "", topN).getContent();

        List<Report> recentReports = reportRepository.search(null, null, doctorId, null, "",
                Instant.EPOCH, Instant.now(), topNByCreatedDesc).getContent();

        List<LeadActivity> recentStageChanges = leadActivityRepository.findRecentByType(
                LeadActivityType.STAGE_CHANGE, doctorId, topN);

        List<DashboardActivityDto> merged = new ArrayList<>();
        for (User patient : recentPatients) {
            merged.add(new DashboardActivityDto("patient_created",
                    patient.getName() + " was added as a patient", patient.getCreatedAt(), patient.getId(), null));
        }
        for (Report report : recentReports) {
            merged.add(new DashboardActivityDto("report_created",
                    report.getTitle() + " uploaded for " + report.getPatient().getName(),
                    report.getCreatedAt(), report.getPatient().getId(), null));
        }
        for (LeadActivity activity : recentStageChanges) {
            String stageLabel = activity.getNewStage() != null ? activity.getNewStage().toWire() : "unknown";
            merged.add(new DashboardActivityDto("lead_stage_change",
                    activity.getLead().getName() + " moved to " + stageLabel,
                    activity.getCreatedAt(), null, activity.getLead().getId()));
        }

        return merged.stream()
                .sorted(Comparator.comparing(DashboardActivityDto::occurredAt).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
    }

    /**
     * DOCTOR-only. A patient qualifies when they have no health record at all
     * ("never_recorded") or their latest one is 30+ days old ("no_recent_report").
     */
    List<AttentionPatientDto> computeAttentionPatients(UUID doctorId) {
        List<UUID> patientIds = userRepository.findPatientIdsOfDoctor(doctorId);
        if (patientIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> patientIdSet = Set.copyOf(patientIds);
        Map<UUID, HealthRecord> latestByPatientId = healthRecordRepository.findLatestPerPatient().stream()
                .filter(hr -> patientIdSet.contains(hr.getPatient().getId()))
                .collect(Collectors.toMap(hr -> hr.getPatient().getId(), hr -> hr));

        Instant staleCutoff = Instant.now().minus(ATTENTION_STALE_DAYS, ChronoUnit.DAYS);
        List<AttentionPatientDto> result = new ArrayList<>();
        for (User patient : userRepository.findAllById(patientIds)) {
            HealthRecord latest = latestByPatientId.get(patient.getId());
            if (latest == null) {
                result.add(new AttentionPatientDto(patient.getId(), patient.getName(), null, "never_recorded"));
            } else if (latest.getRecordedAt().isBefore(staleCutoff)) {
                result.add(new AttentionPatientDto(patient.getId(), patient.getName(),
                        latest.getRecordedAt(), "no_recent_report"));
            }
        }
        return result;
    }

    /** ADMIN-only. */
    List<DoctorPatientCountDto> computeAssignmentOverview() {
        return userRepository.countPatientsPerDoctor(Role.DOCTOR).stream()
                .map(row -> new DoctorPatientCountDto(row.getDoctorId(), row.getDoctorName(), nz(row.getPatientCount())))
                .toList();
    }

    // ---- helpers -------------------------------------------------------

    private BigDecimal bmiOf(HealthRecord record) {
        BigDecimal weight = record.getWeightKg();
        BigDecimal heightCm = patientProfileRepository.findByUserId(record.getPatient().getId())
                .map(PatientProfile::getHeightCm)
                .orElse(null);
        if (weight == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weight.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }
}
