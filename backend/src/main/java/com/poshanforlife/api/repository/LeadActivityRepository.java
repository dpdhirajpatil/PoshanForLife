package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.LeadActivity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    @EntityGraph(attributePaths = {"createdBy"})
    List<LeadActivity> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
}
