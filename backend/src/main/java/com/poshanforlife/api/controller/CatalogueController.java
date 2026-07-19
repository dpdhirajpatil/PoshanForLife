package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CatalogueItemDto;
import com.poshanforlife.api.dto.CreateCatalogueItemRequest;
import com.poshanforlife.api.dto.UpdateCatalogueItemRequest;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.CatalogueService;
import com.poshanforlife.api.service.SupabaseStorageService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One controller for the three parallel catalogue resource groups —
 * {type} binds to programmes | sessions | challenges (converter registered
 * in WebConfig). Reads are ADMIN+DOCTOR; every write is ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/catalogue/{type}")
@RequiredArgsConstructor
@AdminOrDoctor
public class CatalogueController {

    private final CatalogueService catalogueService;
    private final SupabaseStorageService storageService;

    @GetMapping
    public ApiResponse<List<CatalogueItemDto>> list(
            @PathVariable CatalogueItemType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        Page<CatalogueItemDto> result = catalogueService.list(type, status, search, page, limit);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CatalogueItemDto> create(@PathVariable CatalogueItemType type,
                                                @Valid @RequestBody CreateCatalogueItemRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(catalogueService.create(type, request, caller));
    }

    /** Shared cover-image upload — same endpoint shape for all three types. */
    @PostMapping("/upload")
    @AdminOnly
    public ApiResponse<Map<String, String>> upload(@PathVariable CatalogueItemType type,
                                                   @RequestParam("file") MultipartFile file) {
        String url = storageService.uploadImage(file, type.getPathSegment());
        return ApiResponse.ok(Map.of("url", url));
    }

    @GetMapping("/{id}")
    public ApiResponse<CatalogueItemDto> get(@PathVariable CatalogueItemType type,
                                             @PathVariable UUID id) {
        return ApiResponse.ok(catalogueService.get(type, id));
    }

    @PatchMapping("/{id}")
    @AdminOnly
    public ApiResponse<CatalogueItemDto> update(@PathVariable CatalogueItemType type,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody UpdateCatalogueItemRequest request) {
        return ApiResponse.ok(catalogueService.update(type, id, request));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable CatalogueItemType type,
                                                    @PathVariable UUID id) {
        catalogueService.delete(type, id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
