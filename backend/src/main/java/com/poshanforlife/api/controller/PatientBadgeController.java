package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.PatientBadgeStatusDto;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.PatientBadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The patient's full badge catalog, annotated with earned/earnedAt — see PatientBadgeService's javadoc for scoping. */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/badges")
@RequiredArgsConstructor
@AdminOrDoctorOrPatient
public class PatientBadgeController {

    private final PatientBadgeService patientBadgeService;

    @GetMapping
    public ApiResponse<List<PatientBadgeStatusDto>> list(@PathVariable UUID patientId,
                                                         @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(patientBadgeService.listForPatient(patientId, caller));
    }
}
