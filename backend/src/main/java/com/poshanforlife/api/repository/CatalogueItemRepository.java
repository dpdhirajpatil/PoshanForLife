package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.CatalogueItem;
import com.poshanforlife.api.entity.CatalogueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Shared base for the three catalogue repositories — the search query is
 * written once and bound to the concrete entity via #{#entityName}.
 */
@NoRepositoryBean
public interface CatalogueItemRepository<T extends CatalogueItem> extends JpaRepository<T, UUID> {

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres). status may be null.
     */
    @EntityGraph(attributePaths = "createdBy")
    @Query("""
            select e from #{#entityName} e
            where (:status is null or e.status = :status)
              and (:search = ''
                   or lower(e.name) like lower(concat('%', :search, '%'))
                   or lower(e.serviceCode) like lower(concat('%', :search, '%'))
                   or lower(e.type) like lower(concat('%', :search, '%')))
            """)
    Page<T> search(@Param("status") CatalogueStatus status,
                   @Param("search") String search,
                   Pageable pageable);
}
