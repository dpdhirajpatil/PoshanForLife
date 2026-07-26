package com.poshanforlife.api.controller;

import com.poshanforlife.api.config.RateLimit;
import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreateReportRequest;
import com.poshanforlife.api.dto.ReportDetailDto;
import com.poshanforlife.api.dto.ReportListResponseDto;
import com.poshanforlife.api.dto.ReportUploadResponseDto;
import com.poshanforlife.api.dto.UpdateReportRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Manual report records + the AI-powered InBody PDF upload pipeline. list
 * and get are ADMIN+DOCTOR+PATIENT (DOCTOR scoped to their patients, PATIENT
 * force-scoped to their own record — see ReportService); create, upload and
 * update remain ADMIN+DOCTOR only; delete is ADMIN-only (hard delete).
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @AdminOrDoctorOrPatient
    public ApiResponse<ReportListResponseDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        ReportService.ReportListResult result = reportService.list(status, type, search, patientId, dateFrom,
                dateTo, page, limit, caller);
        ReportListResponseDto body = new ReportListResponseDto(result.page().getContent(), result.stats());
        return ApiResponse.ok(body, result.page().getTotalElements(), page, limit);
    }

    @PostMapping
    @AdminOrDoctor
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportDetailDto> create(@Valid @RequestBody CreateReportRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(reportService.create(request, caller));
    }

    /** RATE LIMITED to 10 requests per IP per hour. */
    @PostMapping("/upload")
    @AdminOrDoctor
    @RateLimit(requests = 10, windowSeconds = 3600)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportUploadResponseDto> upload(@RequestParam("patientId") UUID patientId,
                                                       @RequestParam("file") MultipartFile file,
                                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(reportService.upload(patientId, file, caller));
    }

    @GetMapping("/{id}")
    @AdminOrDoctorOrPatient
    public ApiResponse<ReportDetailDto> get(@PathVariable UUID id,
                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(reportService.get(id, caller));
    }

    @PatchMapping("/{id}")
    @AdminOrDoctor
    public ApiResponse<ReportDetailDto> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateReportRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(reportService.update(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        reportService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
