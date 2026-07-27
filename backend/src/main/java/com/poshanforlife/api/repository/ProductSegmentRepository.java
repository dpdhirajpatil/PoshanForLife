package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.ProductSegment;
import com.poshanforlife.api.entity.SegmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSegmentRepository extends JpaRepository<ProductSegment, UUID> {

    List<ProductSegment> findByStatusOrderByDisplayOrderAsc(SegmentStatus status);

    List<ProductSegment> findAllByOrderByDisplayOrderAsc();

    Optional<ProductSegment> findByNameIgnoreCase(String name);

    @Query("select coalesce(max(s.displayOrder), -1) from ProductSegment s")
    int findMaxDisplayOrder();
}
