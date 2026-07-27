package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Product;
import com.poshanforlife.api.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    long countBySegmentId(UUID segmentId);

    @Query("select coalesce(max(p.displayOrder), -1) from Product p where p.segment.id = :segmentId")
    int findMaxDisplayOrder(@Param("segmentId") UUID segmentId);

    /**
     * search must be non-null — pass "" for no filter (null String params in
     * lower()/concat() bind as bytea on Postgres, per this codebase's
     * established JPQL convention).
     */
    @EntityGraph(attributePaths = {"createdBy", "segment"})
    @Query("""
            select p from Product p
            where (:segmentId is null or p.segment.id = :segmentId)
              and (:status is null or p.status = :status)
              and (:search = ''
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(p.sku) like lower(concat('%', :search, '%')))
            """)
    Page<Product> search(@Param("segmentId") UUID segmentId,
                         @Param("status") ProductStatus status,
                         @Param("search") String search,
                         Pageable pageable);

    @Query("select p.segment.id as segmentId, count(p) as total from Product p "
            + "where p.status = :status group by p.segment.id")
    List<SegmentProductCount> countByStatusGroupBySegment(@Param("status") ProductStatus status);

    interface SegmentProductCount {
        UUID getSegmentId();
        long getTotal();
    }
}
