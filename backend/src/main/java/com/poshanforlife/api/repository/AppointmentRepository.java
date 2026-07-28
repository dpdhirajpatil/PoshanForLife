package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Appointment;
import com.poshanforlife.api.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * patientId/practitionerId/status are each optional (null = unscoped);
     * from/to are always set by the service (open-ended range instead of a
     * null-typed timestamp param). Exactly one of patientId/practitionerId is
     * non-null for a PATIENT/DOCTOR caller; both null for ADMIN.
     */
    @EntityGraph(attributePaths = {"patient", "practitioner", "createdBy"})
    @Query("""
            select a from Appointment a
            where (:patientId is null or a.patient.id = :patientId)
              and (:practitionerId is null or a.practitioner.id = :practitionerId)
              and (:status is null or a.status = :status)
              and a.scheduledAt >= :from and a.scheduledAt < :to
            """)
    Page<Appointment> search(@Param("patientId") UUID patientId,
                             @Param("practitionerId") UUID practitionerId,
                             @Param("status") AppointmentStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    /** Booked (non-cancelled) slots for a practitioner on a given day — used for both the calendar view and available-slots computation. */
    List<Appointment> findByPractitionerIdAndScheduledAtBetweenAndStatusNot(
            UUID practitionerId, Instant from, Instant to, AppointmentStatus excludedStatus);

    boolean existsByPractitionerIdAndScheduledAtAndStatusNot(
            UUID practitionerId, Instant scheduledAt, AppointmentStatus excludedStatus);
}
