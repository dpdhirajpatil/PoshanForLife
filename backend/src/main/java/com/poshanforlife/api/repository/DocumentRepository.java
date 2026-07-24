package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Document;
import com.poshanforlife.api.entity.DocumentStatus;
import com.poshanforlife.api.entity.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * doctorId scopes a DOCTOR caller to documents whose lead is assigned to
     * them, or whose patient is one of theirs (via the DoctorPatient link) —
     * null (ADMIN) sees everything matching the other filters.
     */
    @EntityGraph(attributePaths = {"lead", "patient", "createdBy"})
    @Query("""
            select d from Document d
            where (:leadId is null or d.lead.id = :leadId)
              and (:patientId is null or d.patient.id = :patientId)
              and (:type is null or d.documentType = :type)
              and (:status is null or d.status = :status)
              and (:doctorId is null
                   or (d.lead is not null and d.lead.assignedPractitioner.id = :doctorId)
                   or (d.patient is not null and exists (
                        select 1 from DoctorPatient dp
                        where dp.doctor.id = :doctorId and dp.patient.id = d.patient.id)))
            """)
    Page<Document> search(@Param("leadId") UUID leadId,
                          @Param("patientId") UUID patientId,
                          @Param("type") DocumentType type,
                          @Param("status") DocumentStatus status,
                          @Param("doctorId") UUID doctorId,
                          Pageable pageable);
}
