package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreatePatientRequest;
import com.poshanforlife.api.dto.PatientDetailDto;
import com.poshanforlife.api.dto.PatientStatsDto;
import com.poshanforlife.api.dto.PatientSummaryDto;
import com.poshanforlife.api.dto.UpdatePatientRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.PatientService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deliberately annotated per-method rather than class-wide: a class-level
 * {@code @AdminOrDoctor} silently 403'd the patient app's own dashboard, which reads
 * {@code GET /patients/{id}} for the caller's own record. Same defect (and same fix)
 * as DocumentController's — keep new endpoints explicitly annotated here.
 */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @AdminOrDoctor
    public ApiResponse<List<PatientSummaryDto>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        Page<PatientSummaryDto> result = patientService.list(search, doctorId, page, limit, caller);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    @GetMapping("/stats")
    @AdminOrDoctor
    public ApiResponse<PatientStatsDto> stats(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientService.stats(caller));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AdminOrDoctor
    public ApiResponse<PatientDetailDto> create(@Valid @RequestBody CreatePatientRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientService.create(request, caller));
    }

    /** PATIENT callers may read only their own record; PatientService enforces that (404 otherwise). */
    @GetMapping("/{id}")
    @AdminOrDoctorOrPatient
    public ApiResponse<PatientDetailDto> get(@PathVariable UUID id,
                                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientService.get(id, caller));
    }

    @PatchMapping("/{id}")
    @AdminOrDoctor
    public ApiResponse<PatientDetailDto> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdatePatientRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientService.update(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> softDelete(@PathVariable UUID id) {
        patientService.softDelete(id);
        return ApiResponse.ok(Map.of("deactivated", true));
    }
}
