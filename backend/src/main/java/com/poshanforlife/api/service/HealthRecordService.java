package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.dto.UpsertHealthRecordRequest;
import com.poshanforlife.api.dto.UpsertHealthRecordResponseDto;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.HealthRecordSource;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.HealthRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link HealthRecord} history + upsert for one patient — powers the mobile
 * app's InBody/manual/wearable trend charts (AN-05) and this same upsert
 * path is now also called directly by PATIENT/LEAD callers (manual tracking
 * screen, Health Connect sync) as well as ADMIN/DOCTOR. Read scoping: ADMIN
 * sees anyone, DOCTOR is scoped to their assigned patients (403 otherwise),
 * PATIENT/LEAD are force-scoped to their own id (404 otherwise — see
 * {@link #requirePatientAccess}). The InBody upload pipeline
 * (ReportService#upsertHealthRecord) keeps its own, separate upsert — it
 * writes from a different shape of data (parsed PDF fields) and always forces
 * source=INBODY_UPLOAD, so duplicating a dozen lines of field-copying here
 * was judged simpler and lower-risk than threading a shared method through
 * that already-working pipeline.
 */
@Service
@RequiredArgsConstructor
public class HealthRecordService {

    private final HealthRecordRepository healthRecordRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional(readOnly = true)
    public List<HealthRecordDto> list(UUID patientId, AuthenticatedUser caller, Integer limit, LocalDate before, Set<String> fields) {
        findPatient(patientId);
        requirePatientAccess(patientId, caller);

        BigDecimal heightCm = heightOf(patientId);
        List<HealthRecordDto> all = HealthRecordMapper.toDtos(
                healthRecordRepository.findByPatientIdOrderByRecordDateAsc(patientId), heightCm, Set.of());

        List<HealthRecordDto> afterCursor = before == null
                ? all
                : all.stream().filter(d -> d.recordDate().isBefore(before)).toList();

        List<HealthRecordDto> limited = (limit == null || limit >= afterCursor.size())
                ? afterCursor
                : afterCursor.subList(afterCursor.size() - limit, afterCursor.size());

        return fields.isEmpty() ? limited : limited.stream().map(d -> HealthRecordMapper.restrict(d, fields)).toList();
    }

    @Transactional
    public UpsertHealthRecordResponseDto upsert(UpsertHealthRecordRequest request, AuthenticatedUser caller) {
        UUID targetId = resolveTargetPatientId(request, caller);
        User patient = findPatient(targetId);
        requirePatientAccess(targetId, caller);

        HealthRecordSource source = resolveSource(request, caller);
        LocalDate recordDate = request.recordDate() != null ? request.recordDate() : LocalDate.now(ZoneOffset.UTC);

        Optional<HealthRecord> existing = healthRecordRepository.findByPatientIdAndRecordDate(targetId, recordDate);
        boolean upserted = existing.isPresent();
        HealthRecord record = existing.orElseGet(HealthRecord::new);
        record.setPatient(patient);
        record.setRecordDate(recordDate);
        record.setRecordedAt(Instant.now());
        record.setSource(source);
        if (request.weightKg() != null) record.setWeightKg(request.weightKg());
        if (request.bodyFatPct() != null) record.setBodyFatPct(request.bodyFatPct());
        if (request.skeletalMuscleMassKg() != null) record.setSkeletalMuscleMassKg(request.skeletalMuscleMassKg());
        if (request.bmi() != null) record.setBmi(request.bmi());
        if (request.visceralFatLevel() != null) record.setVisceralFatLevel(request.visceralFatLevel());
        if (request.bodyWaterL() != null) record.setBodyWaterL(request.bodyWaterL());
        if (request.proteinKg() != null) record.setProteinKg(request.proteinKg());
        if (request.mineralKg() != null) record.setMineralKg(request.mineralKg());
        if (request.basalMetabolicRate() != null) record.setBasalMetabolicRate(request.basalMetabolicRate());
        UUID savedId = healthRecordRepository.save(record).getId();

        BigDecimal heightCm = heightOf(targetId);
        HealthRecordDto dto = HealthRecordMapper.toDtos(
                        healthRecordRepository.findByPatientIdOrderByRecordDateAsc(targetId), heightCm, Set.of())
                .stream()
                .filter(d -> d.id().equals(savedId.toString()))
                .findFirst()
                .orElseThrow();

        return new UpsertHealthRecordResponseDto(dto, upserted);
    }

    /** Hard-deletes one specific record; {@code patientId} is a scoping guard, the record id is the real target. */
    @Transactional
    public void delete(UUID patientId, UUID recordId) {
        HealthRecord record = healthRecordRepository.findById(recordId)
                .filter(hr -> hr.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("HealthRecord", recordId));
        healthRecordRepository.delete(record);
    }

    private UUID resolveTargetPatientId(UpsertHealthRecordRequest request, AuthenticatedUser caller) {
        if (caller.role() == Role.PATIENT || caller.role() == Role.LEAD) {
            if (request.patientId() != null && !request.patientId().equals(caller.id())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "patientId is not accepted for this role — you can only log your own records");
            }
            return UUID.fromString(caller.id());
        }
        if (request.patientId() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "patientId is required",
                    Map.of("patientId", "patientId is required"));
        }
        return UUID.fromString(request.patientId());
    }

    private HealthRecordSource resolveSource(UpsertHealthRecordRequest request, AuthenticatedUser caller) {
        if (caller.role() == Role.ADMIN || caller.role() == Role.DOCTOR) {
            return HealthRecordSource.MANUAL;
        }
        if (request.source() != HealthRecordSource.PATIENT_MANUAL && request.source() != HealthRecordSource.WEARABLE_SYNC) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "source must be patient_manual or wearable_sync for this role",
                    Map.of("source", "must be patient_manual or wearable_sync"));
        }
        return request.source();
    }

    private BigDecimal heightOf(UUID patientId) {
        return patientProfileRepository.findByUserId(patientId).map(PatientProfile::getHeightCm).orElse(null);
    }

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT || u.getRole() == Role.LEAD)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /**
     * A PATIENT/LEAD gets a plain 404 (not 403) on anyone else's id — this must
     * not confirm to the caller that a patient with that id exists, the same
     * reasoning as {@link ReportService#get}. DOCTOR keeps the existing 403
     * AccessDeniedException behavior for an unassigned patient; that check is
     * skipped for a self-write/self-read since there's no "assignment" to check.
     */
    private void requirePatientAccess(UUID patientId, AuthenticatedUser caller) {
        if ((caller.role() == Role.PATIENT || caller.role() == Role.LEAD)
                && !patientId.toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                        UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }
}
