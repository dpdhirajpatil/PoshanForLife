package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Lead;
import com.poshanforlife.api.entity.LeadSource;
import com.poshanforlife.api.entity.LeadStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres). stage/doctorId/source
     * null = unfiltered; followupToday false = no follow-up-date filter.
     * Keep this WHERE clause in sync with {@link #countStats} below, MINUS
     * the stage predicate — the summary's stage breakdown intentionally
     * ignores the list's own stage filter (see LeadService).
     */
    @EntityGraph(attributePaths = {"assignedPractitioner", "interestedProgramme", "createdBy", "convertedPatient"})
    @Query("""
            select l from Lead l
            where (:stage is null or l.stage = :stage)
              and (:doctorId is null or l.assignedPractitioner.id = :doctorId)
              and (:source is null or l.source = :source)
              and (:followupToday = false
                   or (l.nextFollowupAt >= :todayStart and l.nextFollowupAt < :todayEnd))
              and (:search = ''
                   or lower(l.name) like lower(concat('%', :search, '%'))
                   or lower(l.phone) like lower(concat('%', :search, '%'))
                   or lower(l.email) like lower(concat('%', :search, '%')))
            """)
    Page<Lead> search(@Param("stage") LeadStage stage,
                      @Param("doctorId") UUID doctorId,
                      @Param("source") LeadSource source,
                      @Param("followupToday") boolean followupToday,
                      @Param("search") String search,
                      @Param("todayStart") Instant todayStart,
                      @Param("todayEnd") Instant todayEnd,
                      Pageable pageable);

    /**
     * Same predicate as {@link #search} minus the stage filter — a
     * stage-by-stage breakdown with an active stage filter applied would be
     * meaningless, so the summary always reflects doctorId/source/search only.
     */
    @Query("""
            select
                count(l) as total,
                sum(case when l.stage = :newStage then 1 else 0 end) as newCount,
                sum(case when l.stage = :contacted then 1 else 0 end) as contacted,
                sum(case when l.stage = :qualified then 1 else 0 end) as qualified,
                sum(case when l.stage = :proposed then 1 else 0 end) as proposed,
                sum(case when l.stage = :converted then 1 else 0 end) as converted,
                sum(case when l.stage = :lost then 1 else 0 end) as lost,
                sum(case when l.nextFollowupAt >= :todayStart and l.nextFollowupAt < :todayEnd
                    then 1 else 0 end) as followupToday,
                sum(case when l.createdAt >= :weekStart then 1 else 0 end) as newThisWeek
            from Lead l
            where (:doctorId is null or l.assignedPractitioner.id = :doctorId)
              and (:source is null or l.source = :source)
              and (:search = ''
                   or lower(l.name) like lower(concat('%', :search, '%'))
                   or lower(l.phone) like lower(concat('%', :search, '%'))
                   or lower(l.email) like lower(concat('%', :search, '%')))
            """)
    LeadStats countStats(@Param("doctorId") UUID doctorId,
                        @Param("source") LeadSource source,
                        @Param("search") String search,
                        @Param("todayStart") Instant todayStart,
                        @Param("todayEnd") Instant todayEnd,
                        @Param("weekStart") Instant weekStart,
                        @Param("newStage") LeadStage newStage,
                        @Param("contacted") LeadStage contacted,
                        @Param("qualified") LeadStage qualified,
                        @Param("proposed") LeadStage proposed,
                        @Param("converted") LeadStage converted,
                        @Param("lost") LeadStage lost);

    interface LeadStats {
        Long getTotal();

        Long getNewCount();

        Long getContacted();

        Long getQualified();

        Long getProposed();

        Long getConverted();

        Long getLost();

        Long getFollowupToday();

        Long getNewThisWeek();
    }
}
