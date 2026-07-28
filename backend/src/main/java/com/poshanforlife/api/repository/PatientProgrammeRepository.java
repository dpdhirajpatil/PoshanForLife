package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.PatientProgramme;
import com.poshanforlife.api.entity.PatientProgrammeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PatientProgrammeRepository extends JpaRepository<PatientProgramme, UUID> {

    List<PatientProgramme> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    /** Used by badge evaluation's programme_count criteria. */
    long countByPatientIdAndServiceTypeAndStatus(UUID patientId, CatalogueItemType serviceType,
                                                 PatientProgrammeStatus status);

    /**
     * Exactly one of the three catalogue columns is set per row, so
     * coalesce(...) is the referenced item's id; serviceType says which table
     * it points into. Used by the catalogue delete guard.
     */
    @Query("""
            select count(pp) from PatientProgramme pp
            where pp.serviceType = :itemType
              and coalesce(pp.programmeId, pp.sessionId, pp.challengeId) = :itemId
              and pp.status = :status
            """)
    long countForItem(@Param("itemType") CatalogueItemType itemType,
                      @Param("itemId") UUID itemId,
                      @Param("status") PatientProgrammeStatus status);

    /** Assignment counts for a page of catalogue items in one query. */
    @Query("""
            select coalesce(pp.programmeId, pp.sessionId, pp.challengeId) as itemId,
                   count(pp) as total
            from PatientProgramme pp
            where pp.serviceType = :itemType
              and coalesce(pp.programmeId, pp.sessionId, pp.challengeId) in :itemIds
              and pp.status = :status
            group by coalesce(pp.programmeId, pp.sessionId, pp.challengeId)
            """)
    List<ItemAssignmentCount> countByItems(@Param("itemType") CatalogueItemType itemType,
                                           @Param("itemIds") Collection<UUID> itemIds,
                                           @Param("status") PatientProgrammeStatus status);

    interface ItemAssignmentCount {
        UUID getItemId();

        long getTotal();
    }
}
