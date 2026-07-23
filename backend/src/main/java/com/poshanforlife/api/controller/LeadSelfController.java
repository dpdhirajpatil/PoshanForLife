package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.RequestConsultationRequest;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.security.LeadOnly;
import com.poshanforlife.api.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/request-consultation")
    public ApiResponse<Map<String, Boolean>> requestConsultation(
            @Valid @RequestBody RequestConsultationRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        leadService.requestConsultation(request, caller);
        return ApiResponse.ok(Map.of("submitted", true));
    }
}
