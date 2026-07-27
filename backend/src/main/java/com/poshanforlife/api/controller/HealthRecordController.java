package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.HealthRecordDto;
import com.poshanforlife.api.dto.UpsertHealthRecordRequest;
import com.poshanforlife.api.dto.UpsertHealthRecordResponseDto;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctorOrPatientOrLead;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.HealthRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * History + upsert for one patient's {@code HealthRecord}s (weight/body
 * fat/BMI/etc. over time) — feeds the mobile app's trend charts (AN-05) and
 * the web portal's patient detail "Health trends" section. ADMIN+DOCTOR+
 * PATIENT+LEAD; scoping (DOCTOR to assigned patients, PATIENT/LEAD to their
 * own record only) is enforced in HealthRecordService. Delete is ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/health-records")
@RequiredArgsConstructor
public class HealthRecordController {

    private final HealthRecordService healthRecordService;

    @GetMapping("/{patientId}")
    @AdminOrDoctorOrPatientOrLead
    public ApiResponse<List<HealthRecordDto>> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before,
            @RequestParam(required = false) String fields,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(healthRecordService.list(patientId, caller, limit, before, parseFields(fields)));
    }

    /**
     * Upserts today's (or an explicit day's) record for the caller's own
     * patientId (PATIENT/LEAD) or an arbitrary one they have access to
     * (ADMIN/DOCTOR) — see HealthRecordService#resolveTargetPatientId.
     */
    @PostMapping
    @AdminOrDoctorOrPatientOrLead
    public ApiResponse<UpsertHealthRecordResponseDto> upsert(
            @Valid @RequestBody UpsertHealthRecordRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(healthRecordService.upsert(request, caller));
    }

    /** {@code recordId} carries the actual delete target; {@code patientId} only scopes it. */
    @DeleteMapping("/{patientId}")
    @AdminOnly
    public ResponseEntity<Void> delete(@PathVariable UUID patientId, @RequestParam UUID recordId) {
        healthRecordService.delete(patientId, recordId);
        return ResponseEntity.noContent().build();
    }

    private static Set<String> parseFields(String fields) {
        if (fields == null || fields.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
