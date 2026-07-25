package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Read-only view over {@link HealthRecord} for one patient, newest first —
 * powers the mobile app's InBody trend charts (AN-05). Same read scoping as
 * {@link ReportService}: ADMIN sees anyone, DOCTOR is scoped to their
 * assigned patients (403 otherwise), PATIENT is force-scoped to their own
 * id (404 otherwise — see {@link #requirePatientAccess}). There is no write
 * path here; records are only ever written by the InBody upload pipeline
 * (see ReportService#upsertHealthRecord).
 */
@Service
@RequiredArgsConstructor
public class HealthRecordService {

    private final HealthRecordRepository healthRecordRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final PatientProfileRepository patientProfileRepository;

    @Transactional(readOnly = true)
    public List<HealthRecordDto> list(UUID patientId, AuthenticatedUser caller) {
        findPatient(patientId);
        requirePatientAccess(patientId, caller);

        BigDecimal heightCm = patientProfileRepository.findByUserId(patientId)
                .map(PatientProfile::getHeightCm)
                .orElse(null);

        return healthRecordRepository.findByPatientIdOrderByRecordedAtDesc(patientId).stream()
                .map(hr -> new HealthRecordDto(
                        hr.getId().toString(),
                        hr.getWeightKg(),
                        hr.getBodyFatPct(),
                        bmi(hr.getWeightKg(), heightCm),
                        hr.getRecordedAt()))
                .toList();
    }

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /**
     * A PATIENT gets a plain 404 (not 403) on anyone else's id — this must
     * not confirm to the caller that a patient with that id exists, the
     * same reasoning as {@link ReportService#get}. DOCTOR keeps the existing
     * 403 AccessDeniedException behavior for an unassigned patient.
     */
    private void requirePatientAccess(UUID patientId, AuthenticatedUser caller) {
        if (caller.role() == Role.PATIENT && !patientId.toString().equals(caller.id())) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(
                        UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    /** BMI = kg / (m^2); null when either input is missing. */
    private static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }
}
