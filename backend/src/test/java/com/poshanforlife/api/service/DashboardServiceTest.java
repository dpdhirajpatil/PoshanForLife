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
import com.poshanforlife.api.entity.HealthRecordSource;
import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadActivity;
import com.poshanforlife.api.entity.LeadActivityType;
import com.poshanforlife.api.entity.LeadStage;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Report;
import com.poshanforlife.api.entity.ReportType;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.LeadActivityRepository;
import com.poshanforlife.api.repository.LeadRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.ReportRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private PatientService patientService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private PatientProfileRepository patientProfileRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private LeadActivityRepository leadActivityRepository;

    private DashboardService dashboardService;

    private User admin;
    private User doctor;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser doctorCaller;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(patientService, userRepository, healthRecordRepository,
                patientProfileRepository, reportRepository, leadRepository, leadActivityRepository);

        admin = newUser("Admin", Role.ADMIN);
        doctor = newUser("Dr Priya", Role.DOCTOR);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);
    }

    // ---- computeKpis -----------------------------------------------------

    @Test
    void computeKpis_admin_myPatientsIsNull() {
        ReportRepository.ReportStats reportStats = reportStats(10L, 2L, 1L, 6L, 1L);
        LeadRepository.LeadStats leadStats = leadStats(4L);
        when(patientService.stats(adminCaller)).thenReturn(new PatientStatsDto(12, 5, BigDecimal.valueOf(22.5), null));
        when(userRepository.countByRoleAndIsActiveTrue(Role.DOCTOR)).thenReturn(3L);
        when(reportRepository.countStats(isNull(), isNull(), isNull(), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(reportStats);
        when(leadRepository.countStats(isNull(), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(leadStats);

        DashboardKpisDto kpis = dashboardService.computeKpis(adminCaller, null, false);

        assertThat(kpis.totalPatients()).isEqualTo(12);
        assertThat(kpis.activeDoctors()).isEqualTo(3);
        assertThat(kpis.reportsThisMonth()).isEqualTo(6);
        assertThat(kpis.avgInBodyScore()).isEqualByComparingTo("22.5");
        assertThat(kpis.myPatients()).isNull();
        assertThat(kpis.pendingReviews()).isEqualTo(3); // pending(2) + processing(1)
        assertThat(kpis.followupToday()).isEqualTo(4);
    }

    @Test
    void computeKpis_doctor_myPatientsEqualsScopedTotal() {
        UUID doctorId = doctor.getId();
        ReportRepository.ReportStats reportStats = reportStats(5L, 0L, 0L, 2L, 0L);
        LeadRepository.LeadStats leadStats = leadStats(1L);
        when(patientService.stats(doctorCaller)).thenReturn(new PatientStatsDto(4, 2, BigDecimal.valueOf(24.0), null));
        when(userRepository.countByRoleAndIsActiveTrue(Role.DOCTOR)).thenReturn(3L);
        when(reportRepository.countStats(isNull(), isNull(), eq(doctorId), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(reportStats);
        when(leadRepository.countStats(eq(doctorId), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(leadStats);

        DashboardKpisDto kpis = dashboardService.computeKpis(doctorCaller, doctorId, true);

        assertThat(kpis.myPatients()).isEqualTo(4L);
        assertThat(kpis.totalPatients()).isEqualTo(4);
    }

    // ---- computePbfTrend ---------------------------------------------------

    @Test
    void computePbfTrend_averagesPerMonth_andNullsMissingMonths() {
        LocalDate thisMonthDay = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        HealthRecord r1 = healthRecord(null, BigDecimal.valueOf(20), null, thisMonthDay);
        HealthRecord r2 = healthRecord(null, BigDecimal.valueOf(24), null, thisMonthDay);
        when(healthRecordRepository.findByRecordDateGreaterThanEqual(any())).thenReturn(List.of(r1, r2));

        List<PbfTrendPointDto> trend = dashboardService.computePbfTrend(null);

        assertThat(trend).hasSize(6);
        PbfTrendPointDto currentMonth = trend.get(trend.size() - 1);
        assertThat(currentMonth.month()).isEqualTo(YearMonth.now(ZoneOffset.UTC).toString());
        assertThat(currentMonth.avgBodyFatPct()).isEqualByComparingTo("22.0");
        assertThat(trend.get(0).avgBodyFatPct()).isNull();
    }

    @Test
    void computePbfTrend_excludesRecordsOutsideVisibleScope() {
        UUID otherPatientId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        HealthRecord hidden = healthRecord(otherPatientId, BigDecimal.valueOf(99), null, today);
        when(healthRecordRepository.findByRecordDateGreaterThanEqual(any())).thenReturn(List.of(hidden));

        List<PbfTrendPointDto> trend = dashboardService.computePbfTrend(Set.of(UUID.randomUUID()));

        assertThat(trend).allSatisfy(p -> assertThat(p.avgBodyFatPct()).isNull());
    }

    // ---- computeBmiDistribution --------------------------------------------

    @Test
    void computeBmiDistribution_bucketsByHeightAndWeight() {
        User p1 = newUser("Patient One", Role.PATIENT);
        User p2 = newUser("Patient Two", Role.PATIENT);
        HealthRecord underweight = healthRecordFor(p1, BigDecimal.valueOf(45), null, null);
        HealthRecord obese = healthRecordFor(p2, BigDecimal.valueOf(100), null, null);
        when(patientProfileRepository.findByUserId(p1.getId()))
                .thenReturn(java.util.Optional.of(profileWithHeight(BigDecimal.valueOf(170))));
        when(patientProfileRepository.findByUserId(p2.getId()))
                .thenReturn(java.util.Optional.of(profileWithHeight(BigDecimal.valueOf(170))));

        List<BmiBucketDto> buckets = dashboardService.computeBmiDistribution(List.of(underweight, obese));

        assertThat(buckets).extracting(BmiBucketDto::label, BmiBucketDto::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Underweight", 1L),
                        org.assertj.core.groups.Tuple.tuple("Normal", 0L),
                        org.assertj.core.groups.Tuple.tuple("Overweight", 0L),
                        org.assertj.core.groups.Tuple.tuple("Obese", 1L));
    }

    @Test
    void computeBmiDistribution_skipsRecordsMissingHeight() {
        User p1 = newUser("Patient One", Role.PATIENT);
        HealthRecord record = healthRecordFor(p1, BigDecimal.valueOf(70), null, null);
        when(patientProfileRepository.findByUserId(p1.getId())).thenReturn(java.util.Optional.empty());

        List<BmiBucketDto> buckets = dashboardService.computeBmiDistribution(List.of(record));

        assertThat(buckets).allSatisfy(b -> assertThat(b.count()).isZero());
    }

    // ---- computeBodyCompositionScatter --------------------------------------

    @Test
    void computeBodyCompositionScatter_filtersIncompleteRecords() {
        User p1 = newUser("Patient One", Role.PATIENT);
        HealthRecord complete = healthRecordFor(p1, null, BigDecimal.valueOf(18), BigDecimal.valueOf(30));
        HealthRecord incomplete = healthRecordFor(p1, null, BigDecimal.valueOf(18), null);

        List<BodyCompositionPointDto> points = dashboardService.computeBodyCompositionScatter(
                List.of(complete, incomplete));

        assertThat(points).hasSize(1);
        assertThat(points.get(0).x()).isEqualByComparingTo("18");
        assertThat(points.get(0).y()).isEqualByComparingTo("30");
    }

    // ---- computeRecentActivity ----------------------------------------------

    @Test
    void computeRecentActivity_mergesAndSortsAllSourcesDescending() {
        Instant now = Instant.now();
        User newPatient = newUser("New Patient", Role.PATIENT);
        newPatient.setCreatedAt(now.minusSeconds(10));
        Report report = new Report();
        report.setTitle("InBody report");
        report.setPatient(newUser("Report Patient", Role.PATIENT));
        report.setType(ReportType.OTHER);
        setCreatedAt(report, now.minusSeconds(20));
        LeadActivity stageChange = new LeadActivity();
        Lead lead = withId(new Lead());
        lead.setName("Some Lead");
        stageChange.setLead(lead);
        stageChange.setNewStage(LeadStage.CONTACTED);
        stageChange.setActivityType(LeadActivityType.STAGE_CHANGE);
        setCreatedAt(stageChange, now.minusSeconds(5));

        when(userRepository.search(eq(Role.PATIENT), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of(newPatient)));
        when(reportRepository.search(isNull(), isNull(), isNull(), isNull(), eq(""), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(report)));
        when(leadActivityRepository.findRecentByType(eq(LeadActivityType.STAGE_CHANGE), isNull(), any()))
                .thenReturn(List.of(stageChange));

        List<DashboardActivityDto> feed = dashboardService.computeRecentActivity(null);

        assertThat(feed).hasSize(3);
        assertThat(feed).extracting(DashboardActivityDto::type)
                .containsExactly("lead_stage_change", "patient_created", "report_created");
    }

    // ---- computeAttentionPatients -------------------------------------------

    @Test
    void computeAttentionPatients_flagsNeverRecordedAndStale() {
        UUID doctorId = doctor.getId();
        User neverRecorded = newUser("Never Recorded", Role.PATIENT);
        User stale = newUser("Stale Patient", Role.PATIENT);
        User upToDate = newUser("Fresh Patient", Role.PATIENT);
        when(userRepository.findPatientIdsOfDoctor(doctorId))
                .thenReturn(List.of(neverRecorded.getId(), stale.getId(), upToDate.getId()));
        when(userRepository.findAllById(List.of(neverRecorded.getId(), stale.getId(), upToDate.getId())))
                .thenReturn(List.of(neverRecorded, stale, upToDate));

        HealthRecord staleRecord = healthRecordFor(stale, BigDecimal.TEN, null, null);
        staleRecord.setRecordedAt(Instant.now().minus(45, ChronoUnit.DAYS));
        HealthRecord freshRecord = healthRecordFor(upToDate, BigDecimal.TEN, null, null);
        freshRecord.setRecordedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(healthRecordRepository.findLatestPerPatient()).thenReturn(List.of(staleRecord, freshRecord));

        List<AttentionPatientDto> attention = dashboardService.computeAttentionPatients(doctorId);

        assertThat(attention).extracting(AttentionPatientDto::patientId, AttentionPatientDto::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(neverRecorded.getId(), "never_recorded"),
                        org.assertj.core.groups.Tuple.tuple(stale.getId(), "no_recent_report"));
    }

    @Test
    void computeAttentionPatients_emptyWhenNoAssignedPatients() {
        when(userRepository.findPatientIdsOfDoctor(doctor.getId())).thenReturn(List.of());

        assertThat(dashboardService.computeAttentionPatients(doctor.getId())).isEmpty();
    }

    // ---- computeAssignmentOverview -------------------------------------------

    @Test
    void computeAssignmentOverview_mapsRepositoryRows() {
        UserRepository.DoctorPatientCount row = mock(UserRepository.DoctorPatientCount.class);
        when(row.getDoctorId()).thenReturn(doctor.getId());
        when(row.getDoctorName()).thenReturn(doctor.getName());
        when(row.getPatientCount()).thenReturn(7L);
        when(userRepository.countPatientsPerDoctor(Role.DOCTOR)).thenReturn(List.of(row));

        List<DoctorPatientCountDto> overview = dashboardService.computeAssignmentOverview();

        assertThat(overview).containsExactly(new DoctorPatientCountDto(doctor.getId(), doctor.getName(), 7L));
    }

    // ---- getStats role branching ---------------------------------------------

    @Test
    void getStats_doctor_getsAttentionPatientsNotAssignmentOverview() {
        UUID doctorId = doctor.getId();
        when(userRepository.findPatientIdsOfDoctor(doctorId)).thenReturn(List.of());
        when(healthRecordRepository.findLatestPerPatient()).thenReturn(List.of());
        when(healthRecordRepository.findByRecordDateGreaterThanEqual(any())).thenReturn(List.of());
        when(patientService.stats(doctorCaller)).thenReturn(new PatientStatsDto(0, 0, null, null));
        when(userRepository.countByRoleAndIsActiveTrue(Role.DOCTOR)).thenReturn(1L);
        ReportRepository.ReportStats reportStats = reportStats(0L, 0L, 0L, 0L, 0L);
        LeadRepository.LeadStats leadStats = leadStats(0L);
        when(reportRepository.countStats(isNull(), isNull(), eq(doctorId), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(reportStats);
        when(leadRepository.countStats(eq(doctorId), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(leadStats);
        lenient().when(userRepository.searchPatientsOfDoctor(eq(doctorId), eq(""), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(reportRepository.search(isNull(), isNull(), eq(doctorId), isNull(), eq(""), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(leadActivityRepository.findRecentByType(eq(LeadActivityType.STAGE_CHANGE), eq(doctorId), any()))
                .thenReturn(List.of());

        DashboardStatsDto stats = dashboardService.getStats(doctorCaller);

        assertThat(stats.attentionPatients()).isNotNull();
        assertThat(stats.assignmentOverview()).isNull();
    }

    @Test
    void getStats_admin_getsAssignmentOverviewNotAttentionPatients() {
        when(healthRecordRepository.findLatestPerPatient()).thenReturn(List.of());
        when(healthRecordRepository.findByRecordDateGreaterThanEqual(any())).thenReturn(List.of());
        when(patientService.stats(adminCaller)).thenReturn(new PatientStatsDto(0, 0, null, null));
        when(userRepository.countByRoleAndIsActiveTrue(Role.DOCTOR)).thenReturn(0L);
        ReportRepository.ReportStats reportStats = reportStats(0L, 0L, 0L, 0L, 0L);
        LeadRepository.LeadStats leadStats = leadStats(0L);
        when(reportRepository.countStats(isNull(), isNull(), isNull(), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(reportStats);
        when(leadRepository.countStats(isNull(), isNull(), eq(""), any(), any(), any(),
                any(), any(), any(), any(), any(), any())).thenReturn(leadStats);
        lenient().when(userRepository.search(eq(Role.PATIENT), eq(""), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(reportRepository.search(isNull(), isNull(), isNull(), isNull(), eq(""), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(leadActivityRepository.findRecentByType(eq(LeadActivityType.STAGE_CHANGE), isNull(), any()))
                .thenReturn(List.of());
        when(userRepository.countPatientsPerDoctor(Role.DOCTOR)).thenReturn(List.of());

        DashboardStatsDto stats = dashboardService.getStats(adminCaller);

        assertThat(stats.assignmentOverview()).isNotNull();
        assertThat(stats.attentionPatients()).isNull();
    }

    // ---- fixtures ------------------------------------------------------------

    private ReportRepository.ReportStats reportStats(long total, long pending, long processing, long thisMonth,
                                                       long error) {
        ReportRepository.ReportStats stats = mock(ReportRepository.ReportStats.class);
        lenient().when(stats.getTotal()).thenReturn(total);
        lenient().when(stats.getPending()).thenReturn(pending);
        lenient().when(stats.getProcessing()).thenReturn(processing);
        lenient().when(stats.getDone()).thenReturn(total - pending - processing - error);
        lenient().when(stats.getError()).thenReturn(error);
        lenient().when(stats.getThisMonth()).thenReturn(thisMonth);
        return stats;
    }

    private LeadRepository.LeadStats leadStats(long followupToday) {
        LeadRepository.LeadStats stats = mock(LeadRepository.LeadStats.class);
        lenient().when(stats.getFollowupToday()).thenReturn(followupToday);
        return stats;
    }

    private HealthRecord healthRecord(UUID patientId, BigDecimal bodyFatPct, BigDecimal smm, LocalDate recordDate) {
        User patient = newUser("Patient", Role.PATIENT);
        if (patientId != null) {
            setId(patient, patientId);
        }
        HealthRecord record = new HealthRecord();
        record.setPatient(patient);
        record.setBodyFatPct(bodyFatPct);
        record.setSkeletalMuscleMassKg(smm);
        record.setRecordDate(recordDate != null ? recordDate : LocalDate.now(ZoneOffset.UTC));
        record.setRecordedAt(Instant.now());
        record.setSource(HealthRecordSource.MANUAL);
        return record;
    }

    private HealthRecord healthRecordFor(User patient, BigDecimal weightKg, BigDecimal bodyFatPct, BigDecimal smm) {
        HealthRecord record = new HealthRecord();
        record.setPatient(patient);
        record.setWeightKg(weightKg);
        record.setBodyFatPct(bodyFatPct);
        record.setSkeletalMuscleMassKg(smm);
        record.setRecordDate(LocalDate.now(ZoneOffset.UTC));
        record.setRecordedAt(Instant.now());
        record.setSource(HealthRecordSource.MANUAL);
        return record;
    }

    private PatientProfile profileWithHeight(BigDecimal heightCm) {
        PatientProfile profile = new PatientProfile();
        profile.setHeightCm(heightCm);
        return profile;
    }

    private User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + UUID.randomUUID() + "@poshan.test");
        user.setRole(role);
        return user;
    }

    private static void setCreatedAt(Object entity, Instant value) {
        try {
            Field field = findField(entity.getClass(), "createdAt");
            field.setAccessible(true);
            field.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field field = findField(entity.getClass(), "id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T withId(T entity) {
        setId(entity, UUID.randomUUID());
        return entity;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
