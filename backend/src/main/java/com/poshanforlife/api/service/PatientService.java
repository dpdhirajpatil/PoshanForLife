package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreatePatientRequest;
import com.poshanforlife.api.dto.DoctorRefDto;
import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.dto.PatientDetailDto;
import com.poshanforlife.api.dto.PatientStatsDto;
import com.poshanforlife.api.dto.PatientSummaryDto;
import com.poshanforlife.api.dto.UpdatePatientRequest;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.HealthRecord;
import com.poshanforlife.api.entity.PatientProfile;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.EmailConflictException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.HealthRecordRepository;
import com.poshanforlife.api.repository.PatientProfileRepository;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private static final String TEMP_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private final UserRepository userRepository;
    private final PatientProfileRepository patientProfileRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    /** DOCTORs are hard-scoped to their assigned patients; doctorId filter is ADMIN-only. */
    @Transactional(readOnly = true)
    public Page<PatientSummaryDto> list(String search, UUID doctorId, int page, int limit,
                                        AuthenticatedUser caller) {
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        var unsorted = PageRequest.of(Math.max(page - 1, 0), limit);

        Page<User> patients;
        if (caller.role() == Role.DOCTOR) {
            patients = userRepository.searchPatientsOfDoctor(UUID.fromString(caller.id()), term, unsorted);
        } else if (doctorId != null) {
            patients = userRepository.searchPatientsOfDoctor(doctorId, term, unsorted);
        } else {
            patients = userRepository.search(Role.PATIENT, term,
                    PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending()));
        }
        return patients.map(this::toSummary);
    }

    /** Creates User (role=PATIENT) + PatientProfile + DoctorPatient link atomically. */
    @Transactional
    public PatientDetailDto create(CreatePatientRequest request, AuthenticatedUser caller) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new EmailConflictException(email);
        }

        User doctor = resolveDoctorToAssign(request.assignedDoctorId(), caller);

        String tempPassword = null;
        String password = request.password();
        if (password == null || password.isBlank()) {
            tempPassword = generateTempPassword();
            password = tempPassword;
            // TODO(notifications prompt): send the temporary password to the
            // patient by email instead of returning it in the response.
            log.info("Generated temporary password for new patient {} (email delivery not yet implemented)", email);
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.PATIENT);
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());
        user = userRepository.save(user);

        PatientProfile profile = new PatientProfile();
        profile.setUser(user);
        profile.setGender(request.gender());
        profile.setBloodGroup(request.bloodGroup());
        profile.setHeightCm(request.heightCm());
        profile.setEmergencyContact(request.emergencyContact());
        profile.setMedicalHistory(request.medicalHistory());
        patientProfileRepository.save(profile);

        if (doctor != null) {
            DoctorPatient link = new DoctorPatient();
            link.setDoctor(doctor);
            link.setPatient(user);
            doctorPatientRepository.save(link);
        }
        return toDetail(user, profile, tempPassword);
    }

    /**
     * The conversion path for a mobile self-signup lead: promotes the SAME
     * User row (role LEAD -> PATIENT) instead of creating a duplicate — the
     * account already has a real password the person chose, so it's left
     * untouched, and no temp password is generated. Called by
     * LeadService.convert when the lead already has a linked LEAD user.
     */
    @Transactional
    public PatientDetailDto promoteExistingUser(UUID userId, CreatePatientRequest request, AuthenticatedUser caller) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getRole() == Role.LEAD)
                .orElseThrow(() -> new ResourceNotFoundException("Lead account", userId));

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new EmailConflictException(email);
        }

        User doctor = resolveDoctorToAssign(request.assignedDoctorId(), caller);

        user.setName(request.name().trim());
        user.setEmail(email);
        user.setRole(Role.PATIENT);
        user.setPhone(request.phone());
        user.setDateOfBirth(request.dateOfBirth());

        PatientProfile profile = new PatientProfile();
        profile.setUser(user);
        profile.setGender(request.gender());
        profile.setBloodGroup(request.bloodGroup());
        profile.setHeightCm(request.heightCm());
        profile.setEmergencyContact(request.emergencyContact());
        profile.setMedicalHistory(request.medicalHistory());
        patientProfileRepository.save(profile);

        if (doctor != null) {
            DoctorPatient link = new DoctorPatient();
            link.setDoctor(doctor);
            link.setPatient(user);
            doctorPatientRepository.save(link);
        }
        return toDetail(user, profile, null);
    }

    @Transactional(readOnly = true)
    public PatientDetailDto get(UUID id, AuthenticatedUser caller) {
        User patient = findPatient(id);
        requireAccess(id, caller);
        PatientProfile profile = patientProfileRepository.findByUserId(id).orElse(null);
        return toDetail(patient, profile, null);
    }

    @Transactional
    public PatientDetailDto update(UUID id, UpdatePatientRequest request, AuthenticatedUser caller) {
        User patient = findPatient(id);
        requireAccess(id, caller);

        if (request.name() != null) patient.setName(request.name().trim());
        if (request.phone() != null) patient.setPhone(request.phone());
        if (request.dateOfBirth() != null) patient.setDateOfBirth(request.dateOfBirth());

        // Profile is created lazily for patients that predate this feature
        PatientProfile profile = patientProfileRepository.findByUserId(id).orElseGet(() -> {
            PatientProfile created = new PatientProfile();
            created.setUser(patient);
            return created;
        });
        if (request.gender() != null) profile.setGender(request.gender());
        if (request.bloodGroup() != null) profile.setBloodGroup(request.bloodGroup());
        if (request.heightCm() != null) profile.setHeightCm(request.heightCm());
        if (request.emergencyContact() != null) profile.setEmergencyContact(request.emergencyContact());
        if (request.medicalHistory() != null) profile.setMedicalHistory(request.medicalHistory());
        patientProfileRepository.save(profile);

        return toDetail(patient, profile, null);
    }

    /** Soft delete — history is kept; the account just can't sign in anymore. */
    @Transactional
    public void softDelete(UUID id) {
        User patient = findPatient(id);
        patient.setActive(false);
        refreshTokenRepository.revokeAllForUser(patient.getId());
    }

    @Transactional(readOnly = true)
    public PatientStatsDto stats(AuthenticatedUser caller) {
        List<User> patients;
        if (caller.role() == Role.DOCTOR) {
            Set<UUID> ids = Set.copyOf(userRepository.findPatientIdsOfDoctor(UUID.fromString(caller.id())));
            patients = ids.isEmpty() ? List.of() : userRepository.findAllById(ids);
        } else {
            patients = userRepository.search(Role.PATIENT, "", PageRequest.of(0, Integer.MAX_VALUE))
                    .getContent();
        }
        Set<UUID> visibleIds = patients.stream().map(User::getId).collect(Collectors.toSet());

        List<HealthRecord> latest = healthRecordRepository.findLatestPerPatient().stream()
                .filter(hr -> visibleIds.contains(hr.getPatient().getId()))
                .toList();

        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<UUID> activeWithRecordThisMonth = latest.stream()
                .filter(hr -> !hr.getRecordedAt().isBefore(monthStart))
                .map(hr -> hr.getPatient().getId())
                .collect(Collectors.toSet());
        long activeThisMonth = patients.stream()
                .filter(User::isActive)
                .filter(p -> !p.getCreatedAt().isBefore(monthStart)
                        || activeWithRecordThisMonth.contains(p.getId()))
                .count();

        return new PatientStatsDto(
                patients.size(),
                activeThisMonth,
                average(latest.stream().map(this::bmiOf).toList()),
                average(latest.stream().map(HealthRecord::getBodyFatPct).toList()));
    }

    // ---- helpers -----------------------------------------------------------

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    /** Shared by create() and promoteExistingUser(): DOCTOR is always self-assigned; ADMIN may pick anyone. */
    private User resolveDoctorToAssign(UUID assignedDoctorId, AuthenticatedUser caller) {
        UUID callerId = UUID.fromString(caller.id());
        UUID doctorToAssign;
        if (caller.role() == Role.DOCTOR) {
            if (assignedDoctorId != null && !assignedDoctorId.equals(callerId)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "Doctors cannot assign patients to another doctor");
            }
            doctorToAssign = callerId;
        } else {
            doctorToAssign = assignedDoctorId;
        }

        if (doctorToAssign == null) {
            return null;
        }
        return userRepository.findById(doctorToAssign)
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "assignedDoctorId does not refer to a DOCTOR user"));
    }

    private void requireAccess(UUID patientId, AuthenticatedUser caller) {
        if (caller.role() == Role.DOCTOR
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(UUID.fromString(caller.id()), patientId)) {
            throw new AccessDeniedException("This patient is not assigned to you");
        }
    }

    private PatientSummaryDto toSummary(User patient) {
        return new PatientSummaryDto(
                patient.getId().toString(),
                patient.getName(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getDateOfBirth(),
                patient.isActive(),
                assignedDoctors(patient.getId()),
                patient.getCreatedAt());
    }

    private PatientDetailDto toDetail(User patient, PatientProfile profile, String tempPassword) {
        BigDecimal heightCm = profile != null ? profile.getHeightCm() : null;
        List<HealthRecordDto> records = healthRecordRepository
                .findByPatientIdOrderByRecordedAtDesc(patient.getId()).stream()
                .map(hr -> new HealthRecordDto(
                        hr.getId().toString(),
                        hr.getWeightKg(),
                        hr.getBodyFatPct(),
                        bmi(hr.getWeightKg(), heightCm),
                        hr.getRecordedAt()))
                .toList();
        return new PatientDetailDto(
                patient.getId().toString(),
                patient.getName(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getDateOfBirth(),
                patient.isActive(),
                profile != null ? profile.getGender() : null,
                profile != null ? profile.getBloodGroup() : null,
                heightCm,
                profile != null ? profile.getEmergencyContact() : null,
                profile != null ? profile.getMedicalHistory() : null,
                profile != null ? profile.getDoctorNotes() : null,
                assignedDoctors(patient.getId()),
                records,
                List.of(), // reports land with the reports feature prompt
                tempPassword,
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }

    private List<DoctorRefDto> assignedDoctors(UUID patientId) {
        return doctorPatientRepository.findByPatientId(patientId).stream()
                .map(dp -> new DoctorRefDto(dp.getDoctor().getId().toString(), dp.getDoctor().getName()))
                .toList();
    }

    private BigDecimal bmiOf(HealthRecord record) {
        BigDecimal height = patientProfileRepository.findByUserId(record.getPatient().getId())
                .map(PatientProfile::getHeightCm)
                .orElse(null);
        return bmi(record.getWeightKg(), height);
    }

    /** BMI = kg / (m^2); null when either input is missing. */
    private static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(v -> v != null).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(present.size()), 1, RoundingMode.HALF_UP);
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 10; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        // guarantee the digit required by the password policy
        sb.append(secureRandom.nextInt(10)).append(secureRandom.nextInt(10));
        return sb.toString();
    }
}
