package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.NotificationListResponseDto;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Any authenticated user (no role restriction — every role, including
 * PATIENT, can have notifications), always scoped to the caller's own rows.
 * Individual mark-as-read isn't exposed — the original API only has "mark
 * all as read"; a per-notification PATCH would be a reasonable future
 * addition but isn't part of the contract being matched here.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationListResponseDto> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "false") boolean unread,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(notificationService.list(caller, limit, unread));
    }

    @PatchMapping
    public ApiResponse<Map<String, Boolean>> markAllRead(@AuthenticationPrincipal AuthenticatedUser caller) {
        notificationService.markAllRead(caller);
        return ApiResponse.ok(Map.of("updated", true));
    }
}
