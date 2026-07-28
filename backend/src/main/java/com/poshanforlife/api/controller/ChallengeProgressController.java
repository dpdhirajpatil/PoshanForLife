package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.ChallengeProgressDto;
import com.poshanforlife.api.dto.CheckInRequest;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.security.PatientOnly;
import com.poshanforlife.api.service.ChallengeProgressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Challenge check-in tracking for one patient_programme assignment. GET is
 * ADMIN+DOCTOR+PATIENT (same scoping as PatientProgrammeController); the
 * check-in PATCH is PATIENT-self-only — a doctor/admin never logs a
 * check-in on a patient's behalf.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/programmes/{ppId}/progress")
@RequiredArgsConstructor
public class ChallengeProgressController {

    private final ChallengeProgressService challengeProgressService;

    @GetMapping
    @AdminOrDoctorOrPatient
    public ApiResponse<ChallengeProgressDto> get(@PathVariable UUID patientId, @PathVariable UUID ppId,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(challengeProgressService.get(patientId, ppId, caller));
    }

    @PatchMapping
    @PatientOnly
    public ApiResponse<ChallengeProgressDto> checkIn(@PathVariable UUID patientId, @PathVariable UUID ppId,
                                                     @Valid @RequestBody CheckInRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(challengeProgressService.checkIn(patientId, ppId, request, caller));
    }
}
