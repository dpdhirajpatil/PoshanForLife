package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.LeadActivity;
import com.poshanforlife.api.entity.LeadActivityType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

    @EntityGraph(attributePaths = {"createdBy"})
    List<LeadActivity> findByLeadIdOrderByCreatedAtAsc(UUID leadId);

    /** Recent activities of one type, newest first — used for the dashboard feed. doctorId null = unfiltered. */
    @EntityGraph(attributePaths = {"lead"})
    @Query("""
            select la from LeadActivity la
            where la.activityType = :type
              and (:doctorId is null or la.lead.assignedPractitioner.id = :doctorId)
            order by la.createdAt desc
            """)
    List<LeadActivity> findRecentByType(@Param("type") LeadActivityType type,
                                        @Param("doctorId") UUID doctorId,
                                        Pageable pageable);
}
