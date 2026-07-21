package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.ConvertLeadRequest;
import com.poshanforlife.api.dto.ConvertLeadResponseDto;
import com.poshanforlife.api.dto.CreateLeadActivityRequest;
import com.poshanforlife.api.dto.CreateLeadRequest;
import com.poshanforlife.api.dto.LeadActivityDto;
import com.poshanforlife.api.dto.LeadDetailDto;
import com.poshanforlife.api.dto.LeadListResponseDto;
import com.poshanforlife.api.dto.ScheduleFollowupRequest;
import com.poshanforlife.api.dto.UpdateLeadRequest;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * CRM leads: pipeline list+summary, detail with activity timeline, the
 * convert-to-patient flow, and follow-up scheduling. ADMIN+DOCTOR; DOCTOR
 * callers are scoped to their own assigned leads in the service layer.
 */
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@AdminOrDoctor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    public ApiResponse<LeadListResponseDto> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "false") boolean followupToday,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        LeadService.LeadListResult result = leadService.list(stage, search, practitionerId, source,
                followupToday, page, limit, caller);
        LeadListResponseDto body = new LeadListResponseDto(result.page().getContent(), result.summary());
        return ApiResponse.ok(body, result.page().getTotalElements(), page, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeadDetailDto> create(@Valid @RequestBody CreateLeadRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ApiResponse<LeadDetailDto> get(@PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.get(id, caller));
    }

    @PatchMapping("/{id}")
    public ApiResponse<LeadDetailDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateLeadRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.update(id, request, caller));
    }

    @PostMapping("/{id}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LeadActivityDto> addActivity(@PathVariable UUID id,
                                                    @Valid @RequestBody CreateLeadActivityRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.addActivity(id, request, caller));
    }

    @PostMapping("/{id}/convert")
    public ApiResponse<ConvertLeadResponseDto> convert(@PathVariable UUID id,
                                                       @Valid @RequestBody ConvertLeadRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.convert(id, request, caller));
    }

    @PostMapping("/{id}/schedule-followup")
    public ApiResponse<LeadDetailDto> scheduleFollowup(@PathVariable UUID id,
                                                       @Valid @RequestBody ScheduleFollowupRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(leadService.scheduleFollowup(id, request, caller));
    }
}
