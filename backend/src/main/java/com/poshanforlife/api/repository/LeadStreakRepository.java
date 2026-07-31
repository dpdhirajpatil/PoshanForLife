package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.LeadStreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeadStreakRepository extends JpaRepository<LeadStreak, UUID> {

    Optional<LeadStreak> findByLeadId(UUID leadUserId);
}
