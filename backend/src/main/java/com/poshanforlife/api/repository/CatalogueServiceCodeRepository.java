package com.poshanforlife.api.repository;

import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.CatalogueServiceCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogueServiceCodeRepository extends JpaRepository<CatalogueServiceCode, UUID> {

    Optional<CatalogueServiceCode> findByCodeIgnoreCase(String code);

    Optional<CatalogueServiceCode> findByItemTypeAndItemId(CatalogueItemType itemType, UUID itemId);
}
