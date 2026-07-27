package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateProductSegmentRequest;
import com.poshanforlife.api.dto.ProductSegmentDto;
import com.poshanforlife.api.dto.UpdateProductSegmentRequest;
import com.poshanforlife.api.entity.ProductSegment;
import com.poshanforlife.api.entity.ProductStatus;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.SegmentStatus;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ProductRepository;
import com.poshanforlife.api.repository.ProductRepository.SegmentProductCount;
import com.poshanforlife.api.repository.ProductSegmentRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Product segments are admin-manageable categories (e.g. "Meal
 * Replacements") rather than a fixed enum, so the product lineup can keep
 * growing without a code change.
 */
@Service
@RequiredArgsConstructor
public class ProductSegmentService {

    private final ProductSegmentRepository segmentRepository;
    private final ProductRepository productRepository;

    /**
     * Non-admin callers always get active-only, regardless of
     * includeArchived — mirrors ProductService.list's "non-admin never sees
     * drafts" rule.
     */
    @Transactional(readOnly = true)
    public List<ProductSegmentDto> list(boolean includeArchived, AuthenticatedUser caller) {
        List<ProductSegment> segments = (includeArchived && caller.role() == Role.ADMIN)
                ? segmentRepository.findAllByOrderByDisplayOrderAsc()
                : segmentRepository.findByStatusOrderByDisplayOrderAsc(SegmentStatus.ACTIVE);

        Map<UUID, Long> publishedCounts = productRepository.countByStatusGroupBySegment(ProductStatus.PUBLISHED)
                .stream()
                .collect(Collectors.toMap(SegmentProductCount::getSegmentId, SegmentProductCount::getTotal));

        return segments.stream()
                .map(s -> toDto(s, publishedCounts.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    @Transactional
    public ProductSegmentDto create(CreateProductSegmentRequest request) {
        String name = request.name().trim();
        ensureNameAvailable(name, null);

        ProductSegment segment = new ProductSegment();
        segment.setName(name);
        segment.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder() : segmentRepository.findMaxDisplayOrder() + 1);
        segment = segmentRepository.save(segment);
        return toDto(segment, 0L);
    }

    @Transactional
    public ProductSegmentDto update(UUID id, UpdateProductSegmentRequest request) {
        ProductSegment segment = find(id);
        if (request.name() != null) {
            String name = request.name().trim();
            ensureNameAvailable(name, id);
            segment.setName(name);
        }
        if (request.displayOrder() != null) segment.setDisplayOrder(request.displayOrder());
        if (request.status() != null) segment.setStatus(request.status());

        long published = productRepository.countByStatusGroupBySegment(ProductStatus.PUBLISHED).stream()
                .filter(c -> c.getSegmentId().equals(id))
                .mapToLong(SegmentProductCount::getTotal)
                .findFirst().orElse(0L);
        return toDto(segment, published);
    }

    /** Blocked (not cascaded) if the segment still has any products — moving/deleting those first is required. */
    @Transactional
    public void delete(UUID id) {
        ProductSegment segment = find(id);
        long productCount = productRepository.countBySegmentId(id);
        if (productCount > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Cannot delete this segment: " + productCount
                            + " product(s) still belong to it. Move or delete them first.");
        }
        segmentRepository.delete(segment);
    }

    private ProductSegment find(UUID id) {
        return segmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product segment", id));
    }

    private void ensureNameAvailable(String name, UUID selfId) {
        segmentRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.VALIDATION_ERROR,
                            "A segment named '" + name + "' already exists",
                            Map.of("name", "This name is already in use"));
                });
    }

    private ProductSegmentDto toDto(ProductSegment segment, long publishedProductCount) {
        return new ProductSegmentDto(
                segment.getId().toString(),
                segment.getName(),
                segment.getDisplayOrder(),
                segment.getStatus(),
                publishedProductCount,
                segment.getCreatedAt());
    }
}
