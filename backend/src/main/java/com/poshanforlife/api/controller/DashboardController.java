package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.DashboardStatsDto;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single role-aware aggregation endpoint backing the dashboard landing page. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@AdminOrDoctor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDto> stats(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(dashboardService.getStats(caller));
    }
}
