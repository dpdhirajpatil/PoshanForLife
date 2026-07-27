package com.poshanforlife.api.service;

import com.poshanforlife.api.dto.CreateProductRequest;
import com.poshanforlife.api.dto.ProductDto;
import com.poshanforlife.api.dto.UpdateProductRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.entity.Product;
import com.poshanforlife.api.entity.ProductSegment;
import com.poshanforlife.api.entity.ProductStatus;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.exception.ApiException;
import com.poshanforlife.api.exception.ErrorCode;
import com.poshanforlife.api.exception.ResourceNotFoundException;
import com.poshanforlife.api.repository.ProductRepository;
import com.poshanforlife.api.repository.ProductSegmentRepository;
import com.poshanforlife.api.repository.UserRepository;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.util.WireEnums;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

/** Physical goods sold to patients — see Product's javadoc for how this differs from CatalogueService. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductSegmentRepository segmentRepository;
    private final UserRepository userRepository;
    private final SupabaseStorageService storageService;

    @Transactional(readOnly = true)
    public Page<ProductDto> list(UUID segmentId, String status, String search, int page, int limit,
                                 AuthenticatedUser caller) {
        ProductStatus statusFilter = WireEnums.parse(status, ProductStatus::fromWire,
                "status must be one of draft, published");
        // Non-admin callers only ever see published products, regardless of what they pass.
        if (caller.role() != Role.ADMIN) {
            statusFilter = ProductStatus.PUBLISHED;
        }
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        Page<Product> products = productRepository.search(segmentId, statusFilter, term,
                PageRequest.of(Math.max(page - 1, 0), limit, Sort.by("displayOrder").ascending()));
        return products.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto get(UUID id, AuthenticatedUser caller) {
        Product product = find(id);
        // Same not-confirming-existence pattern as Reports: a non-admin asking
        // for a draft product gets a plain 404, not a 403.
        if (caller.role() != Role.ADMIN && product.getStatus() != ProductStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Product", id);
        }
        return toDto(product);
    }

    @Transactional
    public ProductDto create(CreateProductRequest request, AuthenticatedUser caller) {
        ProductSegment segment = findSegment(request.segmentId());
        User creator = userRepository.findById(UUID.fromString(caller.id()))
                .orElseThrow(() -> new ResourceNotFoundException("User", UUID.fromString(caller.id())));

        Product product = new Product();
        product.setSegment(segment);
        product.setName(request.name().trim());
        product.setDescription(blankToNull(request.description()));
        product.setPriceInr(request.priceInr());
        product.setSku(blankToNull(request.sku()));
        product.setStatus(request.status() != null ? request.status() : ProductStatus.DRAFT);
        product.setDisplayOrder(request.displayOrder() != null
                ? request.displayOrder() : productRepository.findMaxDisplayOrder(segment.getId()) + 1);
        product.setCreatedBy(creator);

        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(UUID id, UpdateProductRequest request) {
        Product product = find(id);
        if (request.segmentId() != null) product.setSegment(findSegment(request.segmentId()));
        if (request.name() != null) product.setName(request.name().trim());
        if (request.description() != null) product.setDescription(blankToNull(request.description()));
        if (request.priceInr() != null) product.setPriceInr(request.priceInr());
        if (request.sku() != null) product.setSku(blankToNull(request.sku()));
        if (request.status() != null) product.setStatus(request.status());
        if (request.displayOrder() != null) product.setDisplayOrder(request.displayOrder());
        return toDto(product);
    }

    /** Hard delete — nothing references a Product yet (no orders/cart), unlike CatalogueItem. */
    @Transactional
    public void delete(UUID id) {
        productRepository.delete(find(id));
    }

    /** Appends the uploaded image's URL to the product's images array. */
    @Transactional
    public ProductDto uploadImage(UUID id, MultipartFile file) {
        Product product = find(id);
        String url = storageService.uploadImage(file, "products");
        product.getImages().add(url);
        return toDto(product);
    }

    @Transactional
    public ProductDto removeImage(UUID id, String imageUrl) {
        Product product = find(id);
        if (!product.getImages().remove(imageUrl)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "This product has no image with that URL");
        }
        return toDto(product);
    }

    private Product find(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private ProductSegment findSegment(UUID segmentId) {
        return segmentRepository.findById(segmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Product segment", segmentId));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ProductDto toDto(Product product) {
        BigDecimal price = product.getPriceInr();
        return new ProductDto(
                product.getId().toString(),
                product.getSegment().getId().toString(),
                product.getSegment().getName(),
                product.getName(),
                product.getDescription(),
                product.getImages(),
                price,
                product.getSku(),
                product.getStatus(),
                product.getDisplayOrder(),
                new UserRefDto(product.getCreatedBy().getId().toString(), product.getCreatedBy().getName()),
                product.getCreatedAt());
        // NOTE: a future "Purchase" prompt's Buy button belongs on the frontend's
        // product card, not here — this DTO already carries everything (price,
        // sku, status) it would need without any backend changes.
    }
}
