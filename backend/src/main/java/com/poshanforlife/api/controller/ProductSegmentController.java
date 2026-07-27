package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreateProductSegmentRequest;
import com.poshanforlife.api.dto.ProductSegmentDto;
import com.poshanforlife.api.dto.UpdateProductSegmentRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.ProductSegmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Segments are admin-manageable product categories. Reads are open to any
 * authenticated role (no annotation needed — SecurityConfig's anyRequest()
 * .authenticated() already covers it); every write is ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/products/segments")
@RequiredArgsConstructor
public class ProductSegmentController {

    private final ProductSegmentService segmentService;

    @GetMapping
    public ApiResponse<List<ProductSegmentDto>> list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(segmentService.list(includeArchived, caller));
    }

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductSegmentDto> create(@Valid @RequestBody CreateProductSegmentRequest request) {
        return ApiResponse.ok(segmentService.create(request));
    }

    @PatchMapping("/{id}")
    @AdminOnly
    public ApiResponse<ProductSegmentDto> update(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateProductSegmentRequest request) {
        return ApiResponse.ok(segmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        segmentService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
