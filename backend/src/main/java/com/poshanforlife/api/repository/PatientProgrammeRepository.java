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

    long countByItemTypeAndItemIdAndStatus(CatalogueItemType itemType, UUID itemId,
                                           PatientProgrammeStatus status);

    /** Assignment counts for a page of catalogue items in one query. */
    @Query("""
            select pp.itemId as itemId, count(pp) as total
            from PatientProgramme pp
            where pp.itemType = :itemType and pp.itemId in :itemIds and pp.status = :status
            group by pp.itemId
            """)
    List<ItemAssignmentCount> countByItems(@Param("itemType") CatalogueItemType itemType,
                                           @Param("itemIds") Collection<UUID> itemIds,
                                           @Param("status") PatientProgrammeStatus status);

    interface ItemAssignmentCount {
        UUID getItemId();

        long getTotal();
    }
}
