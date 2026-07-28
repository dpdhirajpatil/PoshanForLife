package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.AppointmentDto;
import com.poshanforlife.api.dto.AvailableSlotDto;
import com.poshanforlife.api.dto.CreateAppointmentRequest;
import com.poshanforlife.api.dto.UpdateAppointmentRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.Appointment;
import com.poshanforlife.api.entity.AppointmentStatus;
import com.poshanforlife.api.entity.Notification;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.AppointmentRepository;
import com.poshanforlife.api.repository.DoctorPatientRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.WireEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Booking is always between a PATIENT and a practitioner they're already
 * assigned to (the same doctor_patients link prompt 04 built) — there is no
 * concept of booking with an unassigned doctor. Reads: ADMIN sees everyone,
 * DOCTOR force-scoped to their own appointments, PATIENT force-scoped to
 * their own. Working hours are a fixed 09:00-17:00, 30-minute-slot day (no
 * per-practitioner schedule configuration exists yet).
 */
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final LocalTime WORKING_START = LocalTime.of(9, 0);
    private static final LocalTime WORKING_END = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final DoctorPatientRepository doctorPatientRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public Page<AppointmentDto> list(UUID patientId, UUID practitionerId, String status,
                                     LocalDate dateFrom, LocalDate dateTo,
                                     int page, int limit, AuthenticatedUser caller) {
        AppointmentStatus statusFilter = WireEnums.parse(status, AppointmentStatus::fromWire,
                "status must be one of scheduled, completed, cancelled, no_show");
        Instant from = dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.EPOCH;
        Instant to = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.parse("9999-12-31T00:00:00Z");
        if (to.isBefore(from)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "dateTo cannot be before dateFrom");
        }

        // PATIENT is force-scoped to their own id regardless of what's passed (same
        // rule as elsewhere — never a 403 here, the value is just silently overridden).
        UUID patientScope = caller.role() == Role.PATIENT ? UUID.fromString(caller.id()) : patientId;
        UUID practitionerScope = caller.role() == Role.DOCTOR ? UUID.fromString(caller.id()) : practitionerId;

        Page<Appointment> result = appointmentRepository.search(patientScope, practitionerScope, statusFilter, from, to,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("scheduledAt")));
        return result.map(AppointmentService::toDto);
    }

    /** The caller's own assigned practitioners — the only ones they may book with. */
    @Transactional(readOnly = true)
    public List<UserRefDto> myPractitioners(AuthenticatedUser caller) {
        return doctorPatientRepository.findByPatientId(UUID.fromString(caller.id())).stream()
                .map(dp -> new UserRefDto(dp.getDoctor().getId().toString(), dp.getDoctor().getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotDto> availableSlots(UUID practitionerId, LocalDate date, AuthenticatedUser caller) {
        User practitioner = findPractitioner(practitionerId);
        requireAssigned(practitioner.getId(), resolvePatientIdForAccessCheck(caller));

        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<LocalTime> booked = appointmentRepository
                .findByPractitionerIdAndScheduledAtBetweenAndStatusNot(
                        practitionerId, dayStart, dayEnd, AppointmentStatus.CANCELLED)
                .stream()
                .map(a -> a.getScheduledAt().atZone(ZoneOffset.UTC).toLocalTime())
                .collect(Collectors.toSet());

        Instant now = Instant.now();
        List<AvailableSlotDto> slots = new ArrayList<>();
        for (LocalTime t = WORKING_START; t.isBefore(WORKING_END); t = t.plusMinutes(SLOT_MINUTES)) {
            boolean isFuture = date.atTime(t).atZone(ZoneOffset.UTC).toInstant().isAfter(now);
            slots.add(new AvailableSlotDto(t, isFuture && !booked.contains(t)));
        }
        return slots;
    }

    @Transactional
    public AppointmentDto create(CreateAppointmentRequest request, AuthenticatedUser caller) {
        UUID patientId = caller.role() == Role.PATIENT
                ? UUID.fromString(caller.id())
                : request.patientId();
        if (patientId == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "patientId is required");
        }
        User patient = findPatient(patientId);
        User practitioner = findPractitioner(request.practitionerId());
        requireAssigned(practitioner.getId(), patient.getId());

        if (appointmentRepository.existsByPractitionerIdAndScheduledAtAndStatusNot(
                practitioner.getId(), request.scheduledAt(), AppointmentStatus.CANCELLED)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This slot is no longer available");
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setPractitioner(practitioner);
        appointment.setScheduledAt(request.scheduledAt());
        appointment.setDurationMinutes(request.durationMinutes() != null ? request.durationMinutes() : 30);
        appointment.setNotes(request.notes());
        appointment.setVideo(request.isVideo() != null && request.isVideo());
        appointment.setCreatedBy(userRepository.getReferenceById(UUID.fromString(caller.id())));
        appointment = appointmentRepository.save(appointment);

        notificationService.create(practitioner, Notification.TYPE_APPOINTMENT_BOOKED, "New appointment booked",
                patient.getName() + " booked an appointment", "patient", patient.getId());

        return toDto(appointment);
    }

    /**
     * scheduledAt present = reschedule (bumps status back to SCHEDULED unless
     * this same request also sets status); status present = cancel/complete;
     * notes present = practitioner's post-appointment notes. A PATIENT caller
     * is restricted to rescheduling (scheduledAt) and/or cancelling
     * (status=CANCELLED only) — they can never set notes or any other status
     * (completed/no_show), that's DOCTOR/ADMIN-only.
     */
    @Transactional
    public AppointmentDto update(UUID id, UpdateAppointmentRequest request, AuthenticatedUser caller) {
        Appointment appointment = findAccessible(id, caller);

        if (caller.role() == Role.PATIENT) {
            if (request.notes() != null) {
                throw new AccessDeniedException("Patients cannot add appointment notes");
            }
            if (request.status() != null && request.status() != AppointmentStatus.CANCELLED) {
                throw new AccessDeniedException("Patients can only cancel an appointment, not set this status");
            }
        }

        if (request.scheduledAt() != null) {
            if (appointmentRepository.existsByPractitionerIdAndScheduledAtAndStatusNot(
                    appointment.getPractitioner().getId(), request.scheduledAt(), AppointmentStatus.CANCELLED)
                    && !request.scheduledAt().equals(appointment.getScheduledAt())) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "This slot is no longer available");
            }
            appointment.setScheduledAt(request.scheduledAt());
            if (request.status() == null && appointment.getStatus() == AppointmentStatus.CANCELLED) {
                appointment.setStatus(AppointmentStatus.SCHEDULED);
            }
        }
        if (request.status() != null) appointment.setStatus(request.status());
        if (request.notes() != null) appointment.setNotes(request.notes().isBlank() ? null : request.notes().trim());

        return toDto(appointment);
    }

    /** Hard delete — ADMIN-only cleanup tool (see AppointmentController); a cancelled-status PATCH is the normal path for everyone else. */
    @Transactional
    public void delete(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        appointmentRepository.delete(appointment);
    }

    // ---- helpers -----------------------------------------------------------

    private Appointment findAccessible(UUID id, AuthenticatedUser caller) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        UUID callerId = UUID.fromString(caller.id());
        boolean owns = switch (caller.role()) {
            case ADMIN -> true;
            case DOCTOR -> appointment.getPractitioner().getId().equals(callerId);
            case PATIENT -> appointment.getPatient().getId().equals(callerId);
            default -> false;
        };
        if (!owns) {
            throw new AccessDeniedException("This appointment does not belong to you");
        }
        return appointment;
    }

    private User findPatient(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.PATIENT)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", id));
    }

    private User findPractitioner(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.DOCTOR)
                .orElseThrow(() -> new ResourceNotFoundException("Practitioner", id));
    }

    private void requireAssigned(UUID practitionerId, UUID patientId) {
        if (patientId != null
                && !doctorPatientRepository.existsByDoctorIdAndPatientId(practitionerId, patientId)) {
            throw new AccessDeniedException("This practitioner is not assigned to the patient");
        }
    }

    /** For availableSlots' access check: PATIENT must be assigned, DOCTOR/ADMIN aren't scoped to a patient. */
    private UUID resolvePatientIdForAccessCheck(AuthenticatedUser caller) {
        return caller.role() == Role.PATIENT ? UUID.fromString(caller.id()) : null;
    }

    private static AppointmentDto toDto(Appointment a) {
        return new AppointmentDto(
                a.getId().toString(),
                new UserRefDto(a.getPatient().getId().toString(), a.getPatient().getName()),
                new UserRefDto(a.getPractitioner().getId().toString(), a.getPractitioner().getName()),
                a.getScheduledAt(),
                a.getDurationMinutes(),
                a.getStatus(),
                a.getNotes(),
                a.isVideo(),
                a.getVideoRoomId(),
                a.getCreatedBy() == null ? null
                        : new UserRefDto(a.getCreatedBy().getId().toString(), a.getCreatedBy().getName()),
                a.getCreatedAt());
    }
}
