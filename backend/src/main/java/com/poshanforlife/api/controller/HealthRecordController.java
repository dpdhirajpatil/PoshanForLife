package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.HealthRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only history for one patient's {@code HealthRecord}s (weight/body
 * fat/BMI over time), newest first — feeds the mobile app's InBody trend
 * charts (AN-05). ADMIN+DOCTOR+PATIENT; scoping (DOCTOR to assigned
 * patients, PATIENT to their own record only) is enforced in
 * HealthRecordService. There is no write endpoint here — records are only
 * ever written by the Reports InBody upload pipeline.
 */
@RestController
@RequestMapping("/api/v1/health-records")
@RequiredArgsConstructor
public class HealthRecordController {

    private final HealthRecordService healthRecordService;

    @GetMapping("/{patientId}")
    @AdminOrDoctorOrPatient
    public ApiResponse<List<HealthRecordDto>> list(@PathVariable UUID patientId,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(healthRecordService.list(patientId, caller));
    }
}
