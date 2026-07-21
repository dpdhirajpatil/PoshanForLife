package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateReportRequest;
import com.poshanforlife.api.dto.ReportDetailDto;
import com.poshanforlife.api.dto.UpdateReportRequest;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.HealthRecordSource;
import com.poshanforlife.api.entity.InBodyData;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Report;
import com.poshanforlife.api.entity.ReportStatus;
import com.poshanforlife.api.entity.ReportType;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.OcrExtractionException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.ReportRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private HealthRecordRepository healthRecordRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorPatientRepository doctorPatientRepository;
    @Mock
    private ReportStorageService reportStorageService;
    @Mock
    private PdfTextExtractionService pdfTextExtractionService;
    @Mock
    private AnthropicExtractionService anthropicExtractionService;
    @Mock
    private NotificationService notificationService;

    private ReportService reportService;

    private User admin;
    private User doctor;
    private User patient;
    private AuthenticatedUser adminCaller;
    private AuthenticatedUser doctorCaller;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, healthRecordRepository, userRepository,
                doctorPatientRepository, reportStorageService, pdfTextExtractionService,
                anthropicExtractionService, notificationService);

        admin = newUser("Admin", Role.ADMIN);
        doctor = newUser("Dr Priya", Role.DOCTOR);
        patient = newUser("Pat Kumar", Role.PATIENT);
        adminCaller = new AuthenticatedUser(admin.getId().toString(), "admin@poshan.test", Role.ADMIN);
        doctorCaller = new AuthenticatedUser(doctor.getId().toString(), "doctor@poshan.test", Role.DOCTOR);

        lenient().when(userRepository.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(userRepository.getReferenceById(doctor.getId())).thenReturn(doctor);
        lenient().when(userRepository.findById(patient.getId()))
                .thenReturn(Optional.of(patient));
        lenient().when(reportRepository.save(any(Report.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(healthRecordRepository.save(any(HealthRecord.class)))
                .thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    @Test
    void createManualReport_savesDoneStatusNoFile() {
        CreateReportRequest request = new CreateReportRequest(patient.getId(), "Blood panel",
                ReportType.LAB, "Routine checkup");

        ReportDetailDto dto = reportService.create(request, adminCaller);

        assertThat(dto.status()).isEqualTo(ReportStatus.DONE);
        assertThat(dto.type()).isEqualTo(ReportType.LAB);
        assertThat(dto.fileUrl()).isNull();
        verify(reportStorageService, never()).createSignedUrl(any());
    }

    @Test
    void doctorCannotCreateReportForUnassignedPatient() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(false);
        CreateReportRequest request = new CreateReportRequest(patient.getId(), "Blood panel",
                ReportType.LAB, null);

        assertThatThrownBy(() -> reportService.create(request, doctorCaller))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctorCanCreateReportForAssignedPatient() {
        when(doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId()))
                .thenReturn(true);
        CreateReportRequest request = new CreateReportRequest(patient.getId(), "Blood panel",
                ReportType.LAB, null);

        ReportDetailDto dto = reportService.create(request, doctorCaller);

        assertThat(dto.status()).isEqualTo(ReportStatus.DONE);
    }

    @Test
    void update_appliesOnlyProvidedFields() {
        Report report = existingReport(ReportStatus.DONE);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        reportService.update(report.getId(), new UpdateReportRequest("New title", null, null, null), adminCaller);

        assertThat(report.getTitle()).isEqualTo("New title");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DONE);
    }

    @Test
    void update_withParsedDataCorrectionsReUpsertsHealthRecord() {
        Report report = existingReport(ReportStatus.DONE);
        report.setPatient(patient);
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(healthRecordRepository.findByPatientIdAndRecordDate(eq(patient.getId()), any()))
                .thenReturn(Optional.empty());
        InBodyData corrected = fullInBodyData();

        reportService.update(report.getId(), new UpdateReportRequest(null, null, null, corrected), adminCaller);

        assertThat(report.getParsedData()).isEqualTo(corrected);
        ArgumentCaptor<HealthRecord> hrCaptor = ArgumentCaptor.forClass(HealthRecord.class);
        verify(healthRecordRepository).save(hrCaptor.capture());
        assertThat(hrCaptor.getValue().getWeightKg()).isEqualByComparingTo(corrected.weightKg());
    }

    @Test
    void delete_bestEffortRemovesStoredFileThenRow() {
        Report report = existingReport(ReportStatus.DONE);
        report.setFileUrl("patient/uuid.pdf");
        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        reportService.delete(report.getId());

        verify(reportStorageService).delete("patient/uuid.pdf");
        verify(reportRepository).delete(report);
    }

    @Test
    void list_computesStatsOverWholeFilteredSetNotJustPage() {
        Report r = existingReport(ReportStatus.DONE);
        Page<Report> page = new PageImpl<>(List.of(r), PageRequest.of(0, 1), 5);
        when(reportRepository.search(any(), any(), any(), eq(""), any(), any(), any())).thenReturn(page);
        ReportRepository.ReportStats stats = mock(ReportRepository.ReportStats.class);
        when(stats.getTotal()).thenReturn(5L);
        when(stats.getPending()).thenReturn(1L);
        when(stats.getProcessing()).thenReturn(0L);
        when(stats.getDone()).thenReturn(3L);
        when(stats.getError()).thenReturn(1L);
        when(stats.getThisMonth()).thenReturn(2L);
        when(reportRepository.countStats(any(), any(), any(), eq(""), any(), any(), any(),
                any(), any(), any(), any())).thenReturn(stats);

        ReportService.ReportListResult result = reportService.list(null, null, null, null, null,
                1, 1, adminCaller);

        assertThat(result.page().getContent()).hasSize(1);
        assertThat(result.stats().total()).isEqualTo(5);
        assertThat(result.stats().done()).isEqualTo(3);
    }

    @Test
    void list_rejectsUnknownStatus() {
        assertThatThrownBy(() -> reportService.list("bogus", null, null, null, null, 1, 10, adminCaller))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void upload_successPersistsDoneReportAndUpsertsHealthRecord() {
        when(reportStorageService.uploadPdf(any(), eq(patient.getId()))).thenReturn("obj/path.pdf");
        when(reportStorageService.createSignedUrl("obj/path.pdf")).thenReturn("https://signed.example/path.pdf");
        PdfTextExtractionService.Extraction extraction =
                new PdfTextExtractionService.Extraction("text", "some extracted text", null);
        when(pdfTextExtractionService.extract(any())).thenReturn(extraction);
        InBodyData data = fullInBodyData();
        when(anthropicExtractionService.extract(extraction))
                .thenReturn(new AnthropicExtractionService.Result(data, "high", data.extractedFieldCount()));
        when(healthRecordRepository.findByPatientIdAndRecordDate(eq(patient.getId()), any()))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "inbody.pdf", "application/pdf", new byte[]{1});
        var response = reportService.upload(patient.getId(), file, adminCaller);

        assertThat(response.confidence()).isEqualTo("high");
        assertThat(response.fileUrl()).isEqualTo("https://signed.example/path.pdf");
        assertThat(response.warnings()).isNull();

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, org.mockito.Mockito.atLeastOnce()).save(reportCaptor.capture());
        Report savedFinal = reportCaptor.getAllValues().get(reportCaptor.getAllValues().size() - 1);
        assertThat(savedFinal.getStatus()).isEqualTo(ReportStatus.DONE);
        assertThat(savedFinal.getExtractionMethod()).isEqualTo("text");

        ArgumentCaptor<HealthRecord> hrCaptor = ArgumentCaptor.forClass(HealthRecord.class);
        verify(healthRecordRepository).save(hrCaptor.capture());
        assertThat(hrCaptor.getValue().getSource()).isEqualTo(HealthRecordSource.INBODY_UPLOAD);
        assertThat(hrCaptor.getValue().getWeightKg()).isEqualByComparingTo(data.weightKg());
        assertThat(hrCaptor.getValue().getRecordDate()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
    }

    @Test
    void upload_lowConfidenceIncludesWarning() {
        when(reportStorageService.uploadPdf(any(), any())).thenReturn("obj/path.pdf");
        when(reportStorageService.createSignedUrl(any())).thenReturn("https://signed.example/path.pdf");
        PdfTextExtractionService.Extraction extraction =
                new PdfTextExtractionService.Extraction("text", "sparse text", null);
        when(pdfTextExtractionService.extract(any())).thenReturn(extraction);
        InBodyData sparse = new InBodyData(BigDecimal.TEN, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
        when(anthropicExtractionService.extract(extraction))
                .thenReturn(new AnthropicExtractionService.Result(sparse, "low", 1));
        when(healthRecordRepository.findByPatientIdAndRecordDate(any(), any())).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "inbody.pdf", "application/pdf", new byte[]{1});
        var response = reportService.upload(patient.getId(), file, adminCaller);

        assertThat(response.confidence()).isEqualTo("low");
        assertThat(response.warnings()).isNotNull().isNotEmpty();
    }

    @Test
    void upload_extractionFailure_marksReportErrorAndThrowsOcrFailed() {
        when(reportStorageService.uploadPdf(any(), any())).thenReturn("obj/path.pdf");
        when(pdfTextExtractionService.extract(any()))
                .thenThrow(new OcrExtractionException("Could not read this PDF"));

        MockMultipartFile file = new MockMultipartFile("file", "inbody.pdf", "application/pdf", new byte[]{1});

        assertThatThrownBy(() -> reportService.upload(patient.getId(), file, adminCaller))
                .isInstanceOf(OcrExtractionException.class);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, org.mockito.Mockito.atLeastOnce()).save(reportCaptor.capture());
        Report savedFinal = reportCaptor.getAllValues().get(reportCaptor.getAllValues().size() - 1);
        assertThat(savedFinal.getStatus()).isEqualTo(ReportStatus.ERROR);
        verify(healthRecordRepository, never()).save(any());
        verify(notificationService).create(eq(admin), eq(Notification.TYPE_PROCESSING_ERROR), any(), any(),
                eq("report"), eq(savedFinal.getId()));
    }

    private Report existingReport(ReportStatus status) {
        Report report = withId(new Report());
        report.setPatient(patient);
        report.setTitle("Existing report");
        report.setType(ReportType.LAB);
        report.setStatus(status);
        report.setCreatedBy(admin);
        return report;
    }

    private static InBodyData fullInBodyData() {
        return new InBodyData(
                new BigDecimal("70.5"), new BigDecimal("18.2"), new BigDecimal("32.1"),
                new BigDecimal("22.4"), new BigDecimal("8"), new BigDecimal("42.3"),
                new BigDecimal("11.2"), new BigDecimal("3.8"), new BigDecimal("1550"),
                new BigDecimal("12.8"), new BigDecimal("57.7"), new BigDecimal("0.85"),
                new BigDecimal("68.0"), new BigDecimal("-2.5"), new BigDecimal("-3.0"),
                new BigDecimal("0.5"), new BigDecimal("105"), new BigDecimal("25.9"),
                new BigDecimal("16.4"), 82);
    }

    private User newUser(String name, Role role) {
        User user = withId(new User());
        user.setName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@poshan.test");
        user.setRole(role);
        return user;
    }

    private static <T> T withId(T entity) {
        try {
            Field field = findIdField(entity.getClass());
            field.setAccessible(true);
            field.set(entity, UUID.randomUUID());
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("id");
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id");
    }
}
