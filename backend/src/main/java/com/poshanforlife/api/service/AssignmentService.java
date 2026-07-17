package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AssignmentDto;
import com.poshanforlife.api.dto.CreateAssignmentRequest;
import com.poshanforlife.api.dto.DoctorRefDto;
import com.poshanforlife.api.entity.DoctorPatient;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.NotificationRepository;
import com.poshanforlife.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fine-grained single-pair assignment CRUD (ADMIN only). The bulk
 * "replace the whole list" flow lives in UserService#assignPatients.
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final DoctorPatientRepository doctorPatientRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<AssignmentDto> list(UUID doctorId, UUID patientId) {
        List<DoctorPatient> assignments;
        if (doctorId != null) {
            assignments = doctorPatientRepository.findByDoctorId(doctorId);
        } else if (patientId != null) {
            assignments = doctorPatientRepository.findByPatientId(patientId);
        } else {
            assignments = doctorPatientRepository.findAll();
        }
        return assignments.stream()
                .filter(a -> patientId == null || a.getPatient().getId().equals(patientId))
                .map(this::toDto)
                .toList();
    }

    /** Creates the link and records an in-app notification for the doctor. */
    @Transactional
    public AssignmentDto create(CreateAssignmentRequest request) {
        User doctor = userRepository.findById(request.doctorId())
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "doctorId does not refer to a DOCTOR user"));
        User patient = userRepository.findById(request.patientId())
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_ERROR,
                        "patientId does not refer to a PATIENT user"));

        if (doctorPatientRepository.existsByDoctorIdAndPatientId(doctor.getId(), patient.getId())) {
            throw new ApiException(ErrorCode.ASSIGNMENT_CONFLICT,
                    patient.getName() + " is already assigned to " + doctor.getName());
        }

        DoctorPatient assignment = new DoctorPatient();
        assignment.setDoctor(doctor);
        assignment.setPatient(patient);
        assignment = doctorPatientRepository.save(assignment);

        Notification notification = new Notification();
        notification.setUser(doctor);
        notification.setType(Notification.TYPE_PATIENT_ASSIGNED);
        notification.setMessage("You've been assigned patient " + patient.getName());
        notificationRepository.save(notification);

        return toDto(assignment);
    }

    /** Removes only the link — never the patient or their history. */
    @Transactional
    public void delete(UUID id) {
        DoctorPatient assignment = doctorPatientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        doctorPatientRepository.delete(assignment);
    }

    private AssignmentDto toDto(DoctorPatient assignment) {
        return new AssignmentDto(
                assignment.getId().toString(),
                new DoctorRefDto(assignment.getDoctor().getId().toString(), assignment.getDoctor().getName()),
                new DoctorRefDto(assignment.getPatient().getId().toString(), assignment.getPatient().getName()),
                assignment.getCreatedAt());
    }
}
