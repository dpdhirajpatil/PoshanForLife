package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.Badge;
import com.poshanforlife.api.entity.BadgeCriteriaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BadgeRepository extends JpaRepository<Badge, UUID> {

    List<Badge> findAllByOrderByCreatedAtDesc();

    List<Badge> findAllByOrderByCreatedAtAsc();

    List<Badge> findByCriteriaType(BadgeCriteriaType criteriaType);
}
