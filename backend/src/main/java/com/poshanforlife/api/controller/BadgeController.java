package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.BadgeDto;
import com.poshanforlife.api.dto.CreateBadgeRequest;
import com.poshanforlife.api.dto.UpdateBadgeRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.service.BadgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The badge catalog — a small, admin-managed reference table. ADMIN-only
 * end to end (unlike, say, ProductSegment's open reads): patients see their
 * own badge status through PatientBadgeController instead.
 */
@RestController
@RequestMapping("/api/v1/badges")
@RequiredArgsConstructor
@AdminOnly
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping
    public ApiResponse<List<BadgeDto>> list() {
        return ApiResponse.ok(badgeService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BadgeDto> create(@Valid @RequestBody CreateBadgeRequest request) {
        return ApiResponse.ok(badgeService.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BadgeDto> update(@PathVariable UUID id, @Valid @RequestBody UpdateBadgeRequest request) {
        return ApiResponse.ok(badgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        badgeService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
