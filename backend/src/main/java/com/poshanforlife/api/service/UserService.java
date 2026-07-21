package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AssignPatientsRequest;
import com.poshanforlife.api.dto.ChangePasswordRequest;
import com.poshanforlife.api.dto.CreateUserRequest;
import com.poshanforlife.api.dto.NotificationPrefsRequest;
import com.poshanforlife.api.dto.UpdateUserRequest;
import com.poshanforlife.api.dto.UserDetailDto;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.EmailConflictException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.mapper.UserMapper;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.RefreshTokenRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SupabaseStorageService storageService;

    @Transactional(readOnly = true)
    public Page<UserDetailDto> list(Role role, String search, int page, int limit) {
        var pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("createdAt").descending());
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        return userRepository.search(role, term, pageable).map(userMapper::toDetailDto);
    }

    @Transactional
    public UserDetailDto create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new EmailConflictException(email);
        }
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setPhone(request.phone());
        return userMapper.toDetailDto(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserDetailDto get(UUID id, AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);
        return userMapper.toDetailDto(find(id));
    }

    /**
     * Admin may update name, phone, role, isActive, dateOfBirth.
     * A non-admin self-update may only change name + phone; any other field
     * present in the body is REJECTED with 422 VALIDATION_ERROR (documented
     * choice: explicit rejection instead of silently ignoring).
     */
    @Transactional
    public UserDetailDto update(UUID id, UpdateUserRequest request, AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);
        boolean isAdmin = caller.role() == Role.ADMIN;

        if (!isAdmin && (request.role() != null || request.isActive() != null || request.dateOfBirth() != null)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only name and phone can be changed on your own profile",
                    Map.of("allowedFields", List.of("name", "phone")));
        }

        User user = find(id);
        if (request.name() != null) user.setName(request.name().trim());
        if (request.phone() != null) user.setPhone(request.phone());
        if (isAdmin) {
            if (request.role() != null) user.setRole(request.role());
            if (request.isActive() != null) user.setActive(request.isActive());
            if (request.dateOfBirth() != null) user.setDateOfBirth(request.dateOfBirth());
        }
        return userMapper.toDetailDto(user);
    }

    /** Soft delete — deactivates the account and revokes its refresh tokens. */
    @Transactional
    public void softDelete(UUID id) {
        User user = find(id);
        user.setActive(false);
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    /**
     * Self change requires the correct currentPassword; an ADMIN changing
     * someone else's password does not. All refresh tokens are revoked.
     */
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request, AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);

        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Validation failed",
                    Map.of("confirmPassword", "must match newPassword"));
        }

        User user = find(id);
        boolean adminActingOnOther = caller.role() == Role.ADMIN && !caller.id().equals(id.toString());
        if (!adminActingOnOther) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Validation failed",
                        Map.of("currentPassword", "is incorrect"));
            }
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(user.getId());
    }

    /** Replaces ALL patient assignments for the doctor in one transaction. */
    @Transactional
    public List<UserDetailDto> assignPatients(UUID doctorId, AssignPatientsRequest request) {
        User doctor = find(doctorId);
        if (doctor.getRole() != Role.DOCTOR) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Patients can only be assigned to a DOCTOR user");
        }

        List<UUID> ids = List.copyOf(new LinkedHashSet<>(request.patientIds()));
        List<User> patients = userRepository.findByIdInAndRole(ids, Role.PATIENT);
        if (patients.size() != ids.size()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "One or more patientIds do not refer to existing PATIENT users");
        }

        doctorPatientRepository.deleteByDoctorId(doctorId);
        for (User patient : patients) {
            DoctorPatient assignment = new DoctorPatient();
            assignment.setDoctor(doctor);
            assignment.setPatient(patient);
            doctorPatientRepository.save(assignment);
        }
        return patients.stream().map(userMapper::toDetailDto).toList();
    }

    @Transactional(readOnly = true)
    public List<UserDetailDto> getAssignedPatients(UUID doctorId) {
        find(doctorId); // 404 if the doctor doesn't exist
        return doctorPatientRepository.findByDoctorId(doctorId).stream()
                .map(dp -> userMapper.toDetailDto(dp.getPatient()))
                .toList();
    }

    /** Merges the provided booleans into the stored prefs; nulls keep current values. */
    @Transactional
    public UserDetailDto updateNotificationPrefs(UUID id, NotificationPrefsRequest request,
                                                 AuthenticatedUser caller) {
        requireSelfOrAdmin(id, caller);
        User user = find(id);
        user.setNotificationPrefs(user.getNotificationPrefs().merge(
                request.inbodyReport(), request.patientAssigned(),
                request.processingErrors(), request.systemAnnouncements()));
        return userMapper.toDetailDto(user);
    }

    /** Self-only: uploads to the "avatars" folder (same public bucket as catalogue covers) and persists the URL. */
    @Transactional
    public UserDetailDto updateAvatar(UUID id, MultipartFile file) {
        User user = find(id);
        user.setAvatarUrl(storageService.uploadImage(file, "avatars"));
        return userMapper.toDetailDto(user);
    }

    private User find(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private void requireSelfOrAdmin(UUID id, AuthenticatedUser caller) {
        if (caller.role() != Role.ADMIN && !caller.id().equals(id.toString())) {
            throw new AccessDeniedException("Not allowed to access this user");
        }
    }
}
