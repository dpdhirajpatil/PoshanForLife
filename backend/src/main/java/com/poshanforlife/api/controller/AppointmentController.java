package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.AppointmentDto;
import com.poshanforlife.api.dto.AvailableSlotDto;
import com.poshanforlife.api.dto.CreateAppointmentRequest;
import com.poshanforlife.api.dto.UpdateAppointmentRequest;
import com.poshanforlife.api.dto.UserRefDto;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@AdminOrDoctorOrPatient
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public ApiResponse<List<AppointmentDto>> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID practitionerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        Page<AppointmentDto> result = appointmentService.list(patientId, practitionerId, status, dateFrom, dateTo,
                page, limit, caller);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    /** The caller's own assigned practitioners — the pool a PATIENT may book with. */
    @GetMapping("/practitioners")
    public ApiResponse<List<UserRefDto>> myPractitioners(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(appointmentService.myPractitioners(caller));
    }

    @GetMapping("/available-slots")
    public ApiResponse<List<AvailableSlotDto>> availableSlots(
            @RequestParam UUID practitionerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(appointmentService.availableSlots(practitionerId, date, caller));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppointmentDto> create(@Valid @RequestBody CreateAppointmentRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(appointmentService.create(request, caller));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AppointmentDto> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateAppointmentRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(appointmentService.update(id, request, caller));
    }

    /** Hard delete — ADMIN-only cleanup tool; a cancelled-status PATCH is the normal path for everyone else. */
    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        appointmentService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
