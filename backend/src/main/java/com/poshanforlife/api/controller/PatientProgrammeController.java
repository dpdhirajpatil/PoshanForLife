package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreatePatientProgrammeRequest;
import com.poshanforlife.api.dto.PatientProgrammeDto;
import com.poshanforlife.api.dto.UpdatePatientProgrammeRequest;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.PatientProgrammeService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service assignments for a patient. ADMIN sees every patient; DOCTOR only
 * their assigned patients (enforced in the service layer for every endpoint).
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/programmes")
@RequiredArgsConstructor
@AdminOrDoctor
public class PatientProgrammeController {

    private final PatientProgrammeService patientProgrammeService;

    @GetMapping
    public ApiResponse<List<PatientProgrammeDto>> list(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientProgrammeService.list(patientId, caller));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PatientProgrammeDto> create(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreatePatientProgrammeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientProgrammeService.create(patientId, request, caller));
    }

    @GetMapping("/{ppId}")
    public ApiResponse<PatientProgrammeDto> get(
            @PathVariable UUID patientId,
            @PathVariable UUID ppId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientProgrammeService.get(patientId, ppId, caller));
    }

    @PatchMapping("/{ppId}")
    public ApiResponse<PatientProgrammeDto> update(
            @PathVariable UUID patientId,
            @PathVariable UUID ppId,
            @Valid @RequestBody UpdatePatientProgrammeRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientProgrammeService.update(patientId, ppId, request, caller));
    }

    @DeleteMapping("/{ppId}")
    public ApiResponse<Map<String, Boolean>> delete(
            @PathVariable UUID patientId,
            @PathVariable UUID ppId,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        patientProgrammeService.delete(patientId, ppId, caller);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
