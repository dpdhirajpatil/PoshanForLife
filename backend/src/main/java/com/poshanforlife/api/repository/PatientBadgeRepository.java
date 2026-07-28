package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.PatientBadge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientBadgeRepository extends JpaRepository<PatientBadge, UUID> {

    @EntityGraph(attributePaths = {"badge"})
    List<PatientBadge> findByPatientId(UUID patientId);

    boolean existsByPatientIdAndBadgeId(UUID patientId, UUID badgeId);
}
