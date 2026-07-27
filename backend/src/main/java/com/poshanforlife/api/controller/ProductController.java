package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreateProductRequest;
import com.poshanforlife.api.dto.ProductDto;
import com.poshanforlife.api.dto.ProductListResponseDto;
import com.poshanforlife.api.dto.UpdateProductRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Physical goods sold to patients — distinct from CatalogueController's
 * services. Reads are open to any authenticated role (scoped to
 * published-only for non-admin inside ProductService); every write is
 * ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<ProductListResponseDto> list(
            @RequestParam(required = false) UUID segmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        Page<ProductDto> result = productService.list(segmentId, status, search, page, limit, caller);
        return ApiResponse.ok(new ProductListResponseDto(result.getContent()), result.getTotalElements(), page, limit);
    }

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDto> create(@Valid @RequestBody CreateProductRequest request,
                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(productService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductDto> get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(productService.get(id, caller));
    }

    @PatchMapping("/{id}")
    @AdminOnly
    public ApiResponse<ProductDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/{id}/upload-image")
    @AdminOnly
    public ApiResponse<ProductDto> uploadImage(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(productService.uploadImage(id, file));
    }

    @DeleteMapping("/{id}/images")
    @AdminOnly
    public ApiResponse<ProductDto> removeImage(@PathVariable UUID id, @RequestParam String url) {
        return ApiResponse.ok(productService.removeImage(id, url));
    }
}
