package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateReportRequest;
import com.poshanforlife.api.dto.PatientRefDto;
import com.poshanforlife.api.dto.ReportDetailDto;
import com.poshanforlife.api.dto.ReportListItemDto;
import com.poshanforlife.api.dto.ReportStatsDto;
import com.poshanforlife.api.dto.ReportUploadResponseDto;
import com.poshanforlife.api.dto.UpdateReportRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.DoctorPatient;
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
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.OcrExtractionException;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.ReportRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.WireEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Manual report records (LAB/PRESCRIPTION/OTHER) plus the AI-powered InBody
 * PDF upload pipeline. Reads (list, get) are ADMIN+DOCTOR+PATIENT — DOCTOR
 * scoped to their assigned patients, PATIENT force-scoped to their own
 * record only. Writes (create, upload, update, delete) remain ADMIN+DOCTOR
 * only — a PATIENT never creates/edits/deletes a report (see
 * ReportController's method-level annotations).
 *
 * <p>Upload is deliberately NOT one single @Transactional method: the initial
 * PROCESSING row must survive even if extraction later fails (status=ERROR is
 * a real, visible outcome, not a rollback), so the report is inserted via a
 * plain repository call before the slow external calls (PDF parse, Claude)
 * run with no DB transaction open, and finalized (success or error) with a
 * second plain save afterward.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final ReportStorageService reportStorageService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final AnthropicExtractionService anthropicExtractionService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public ReportListResult list(String status, String type, String search, UUID patientIdFilter,
                                 LocalDate dateFrom, LocalDate dateTo, int page, int limit,
                                 AuthenticatedUser caller) {
        ReportStatus statusFilter = WireEnums.parse(status, ReportStatus::fromWire,
                "status must be one of pending, processing, done, error");
        ReportType typeFilter = WireEnums.parse(type, ReportType::fromWire,
                "type must be one of inbody, lab, prescription, other");
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        Instant from = dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse("9999-12-31T00:00:00Z");
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "dateTo cannot be before dateFrom");
        }
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        // DOCTOR is scoped to their assigned patients; PATIENT is force-scoped to their
        // own record regardless of any patientId the caller might try to pass — a PATIENT
        // has no notion of "someone else's reports" at this endpoint. ADMIN/DOCTOR may
        // additionally narrow to one patient via patientIdFilter (e.g. AN-09's patient
        // detail screen) — safe for DOCTOR even without an extra access check, since the
        // doctorId scoping below is ANDed in and already excludes unassigned patients.
        UUID doctorId = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : null;
        UUID patientId = caller.role() == Role.PATIENT ? UUID.fromString(caller.id()) : patientIdFilter;

        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending());
        Page<Report> result = reportRepository.search(statusFilter, typeFilter, doctorId, patientId,
                term, from, to, pageable);
        ReportRepository.ReportStats stats = reportRepository.countStats(statusFilter, typeFilter, doctorId,
                patientId, term, from, to, monthStart,
                ReportStatus.PENDING, ReportStatus.PROCESSING, ReportStatus.DONE, ReportStatus.ERROR);

        return new ReportListResult(result.map(this::toListItem), toStatsDto(stats));
    }

    @Transactional
    public ReportDetailDto create(CreateReportRequest request, AuthenticatedUser caller) {
        User patient = findPatient(request.patientId());
        requireAccess(request.patientId(), caller);
        User creator = userRepository.getReferenceById(UUID.fromString(caller.id()));

        Report report = new Report();
        report.setPatient(patient);
        report.setTitle(request.title());
        report.setType(request.type());
        report.setNotes(request.notes());
        report.setStatus(ReportStatus.DONE);
        report.setCreatedBy(creator);
        report = reportRepository.save(report);
        return toDetail(report);
    }

    @Transactional(readOnly = true)
    public ReportDetailDto get(UUID id, AuthenticatedUser caller) {
        Report report = findReport(id);
        // A PATIENT gets a plain 404 (not 403) on someone else's report id — unlike the
        // DOCTOR/ADMIN AccessDeniedException path below, this must not confirm to the
        // caller that a report with that id exists for a patient other than themselves.
        if (caller.role() == Role.PATIENT && !report.getPatient().getId().toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Report", id);
        }
        requireAccess(report.getPatient().getId(), caller);
        return toDetail(report);
    }

    @Transactional
    public ReportDetailDto update(UUID id, UpdateReportRequest request, AuthenticatedUser caller) {
        Report report = findReport(id);
        requireAccess(report.getPatient().getId(), caller);

        if (request.title() != null) report.setTitle(request.title());
        if (request.notes() != null) report.setNotes(request.notes());
        if (request.status() != null) report.setStatus(request.status());
        if (request.parsedData() != null) {
            report.setParsedData(request.parsedData());
            report.setExtractedFieldCount(request.parsedData().extractedFieldCount());
            upsertHealthRecord(report.getPatient(), request.parsedData());
        }
        return toDetail(reportRepository.save(report));
    }

    @Transactional
    public void delete(UUID id) {
        Report report = findReport(id);
        reportStorageService.delete(report.getFileUrl());
        reportRepository.delete(report);
    }

    /** Steps a-h of the upload pipeline described in the reports prompt. */
    public ReportUploadResponseDto upload(UUID patientId, MultipartFile file, AuthenticatedUser caller) {
        User patient = findPatient(patientId);
        requireAccess(patientId, caller);
        User creator = userRepository.getReferenceById(UUID.fromString(caller.id()));

        // (a)+(b): validate and upload the raw PDF before any Report row exists.
        String objectPath = reportStorageService.uploadPdf(file, patientId);

        Report report = new Report();
        report.setPatient(patient);
        report.setTitle("InBody report - " + LocalDate.now(ZoneOffset.UTC));
        report.setType(ReportType.INBODY);
        report.setStatus(ReportStatus.PROCESSING);
        report.setFileUrl(objectPath);
        report.setCreatedBy(creator);
        report = reportRepository.save(report);

        try {
            // (c)+(d): extract text (or fall back to a page image) and call Claude.
            PdfTextExtractionService.Extraction extraction = pdfTextExtractionService.extract(file);
            AnthropicExtractionService.Result result = anthropicExtractionService.extract(extraction);

            // (e)+(f): persist success and upsert the HealthRecord.
            HealthRecord healthRecord = upsertHealthRecord(patient, result.parsedData());

            report.setStatus(ReportStatus.DONE);
            report.setParsedData(result.parsedData());
            report.setConfidence(result.confidence());
            report.setExtractedFieldCount(result.extractedFieldCount());
            report.setExtractionMethod(extraction.method());
            reportRepository.save(report);

            List<String> warnings = "low".equals(result.confidence())
                    ? List.of("Only " + result.extractedFieldCount() + " of " + InBodyData.totalFieldCount()
                        + " expected fields were found in this report — please review the values below before saving.")
                    : List.of();

            for (DoctorPatient dp : doctorPatientRepository.findByPatientId(patient.getId())) {
                notificationService.create(dp.getDoctor(), Notification.TYPE_INBODY_REPORT, "InBody report ready",
                        "A new InBody report is ready for " + patient.getName(), "report", report.getId());
            }

            return new ReportUploadResponseDto(report.getId().toString(), healthRecord.getId().toString(),
                    result.parsedData(), reportStorageService.createSignedUrl(objectPath),
                    result.confidence(), result.extractedFieldCount(), extraction.method(),
                    warnings.isEmpty() ? null : warnings);
        } catch (RuntimeException e) {
            // (g): the Report row survives as a visible ERROR outcome — never silently fail.
            log.warn("InBody extraction failed for report {}", report.getId(), e);
            report.setStatus(ReportStatus.ERROR);
            reportRepository.save(report);
            notificationService.create(creator, Notification.TYPE_PROCESSING_ERROR, "Report processing failed",
                    "Extraction failed for " + report.getTitle() + " (" + patient.getName() + ")",
                    "report", report.getId());
            throw e instanceof OcrExtractionException
                    ? e
                    : new OcrExtractionException("Could not extract data from this report", e);
        }
    }

    private HealthRecord upsertHealthRecord(User patient, InBodyData data) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        HealthRecord record = healthRecordRepository.findByPatientIdAndRecordDate(patient.getId(), today)
                .orElseGet(HealthRecord::new);
        record.setPatient(patient);
        record.setRecordDate(today);
        record.setRecordedAt(Instant.now());
        record.setSource(HealthRecordSource.INBODY_UPLOAD);
        if (data.weightKg() != null) record.setWeightKg(data.weightKg());
        if (data.bodyFatPercent() != null) record.setBodyFatPct(data.bodyFatPercent());
        if (data.skeletalMuscleMassKg() != null) record.setSkeletalMuscleMassKg(data.skeletalMuscleMassKg());
        if (data.bmi() != null) record.setBmi(data.bmi());
        if (data.visceralFatLevel() != null) record.setVisceralFatLevel(data.visceralFatLevel());
        if (data.bodyWaterL() != null) record.setBodyWaterL(data.bodyWaterL());
        if (data.proteinKg() != null) record.setProteinKg(data.proteinKg());
        if (data.mineralKg() != null) record.setMineralKg(data.mineralKg());
        if (data.basalMetabolicRate() != null) record.setBasalMetabolicRate(data.basalMetabolicRate());
        return healthRecordRepository.save(record);
    }

    private Report findReport(UUID id) {
        return reportRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Report", id));
    }

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private void requireAccess(UUID patientId, AuthenticatedUser caller) {
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                        UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    private ReportListItemDto toListItem(Report r) {
        return new ReportListItemDto(r.getId().toString(), patientRef(r.getPatient()), r.getTitle(),
                r.getType(), r.getStatus(), r.getCreatedAt(), userRef(r.getCreatedBy()));
    }

    private ReportDetailDto toDetail(Report r) {
        String signedUrl = r.getFileUrl() != null ? reportStorageService.createSignedUrl(r.getFileUrl()) : null;
        return new ReportDetailDto(r.getId().toString(), patientRef(r.getPatient()), r.getTitle(), r.getType(),
                r.getNotes(), r.getStatus(), signedUrl, r.getParsedData(), r.getConfidence(),
                r.getExtractedFieldCount(), r.getExtractionMethod(), userRef(r.getCreatedBy()), r.getCreatedAt());
    }

    private static PatientRefDto patientRef(User u) {
        return new PatientRefDto(u.getId().toString(), u.getName(), u.getEmail(), u.getPhone());
    }

    private static UserRefDto userRef(User u) {
        return new UserRefDto(u.getId().toString(), u.getName());
    }

    private static ReportStatsDto toStatsDto(ReportRepository.ReportStats stats) {
        return new ReportStatsDto(
                nz(stats.getTotal()), nz(stats.getPending()), nz(stats.getProcessing()),
                nz(stats.getDone()), nz(stats.getError()), nz(stats.getThisMonth()));
    }

    private static long nz(Long value) {
        return value != null ? value : 0L;
    }

    public record ReportListResult(Page<ReportListItemDto> page, ReportStatsDto stats) {
    }
}
