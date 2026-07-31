package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.LeadStreakDto;
import com.poshanforlife.api.dto.PatientBadgeStatusDto;
import com.poshanforlife.api.dto.RequestConsultationRequest;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.security.LeadOnly;
import com.poshanforlife.api.service.LeadBadgeService;
import com.poshanforlife.api.service.LeadService;
import com.poshanforlife.api.service.LeadStreakService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Self-service endpoints for a mobile self-signup account (role LEAD) to act
 * on their own Lead record — kept separate from LeadController, which is
 * class-level @AdminOrDoctor for the staff-facing CRM endpoints.
 */
@RestController
@RequestMapping("/api/v1/leads/me")
@RequiredArgsConstructor
@LeadOnly
public class LeadSelfController {

    private final LeadService leadService;
    private final LeadStreakService leadStreakService;
    private final LeadBadgeService leadBadgeService;

    @PostMapping("/request-consultation")
    public ApiResponse<Map<String, Boolean>> requestConsultation(
            @Valid @RequestBody RequestConsultationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        leadService.requestConsultation(request, caller);
        return ApiResponse.ok(Map.of("submitted", true));
    }

    /** AN-22: a lightweight, standalone streak (no PatientProgramme backing it — see LeadStreakService). */
    @GetMapping("/streak")
    public ApiResponse<LeadStreakDto> streak(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadStreakService.get(caller));
    }

    @PatchMapping("/streak/check-in")
    public ApiResponse<LeadStreakDto> checkIn(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadStreakService.checkIn(caller));
    }

    /** Only STREAK_DAYS-criteria badges — see LeadBadgeService's javadoc for why the rest are excluded. */
    @GetMapping("/badges")
    public ApiResponse<List<PatientBadgeStatusDto>> badges(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadBadgeService.listForSelf(caller));
    }
}
