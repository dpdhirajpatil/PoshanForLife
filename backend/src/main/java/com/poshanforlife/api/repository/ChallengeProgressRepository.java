package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.ChallengeProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChallengeProgressRepository extends JpaRepository<ChallengeProgress, UUID> {

    Optional<ChallengeProgress> findByPatientProgrammeId(UUID patientProgrammeId);
}
