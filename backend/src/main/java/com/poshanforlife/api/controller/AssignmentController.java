package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.AssignmentDto;
import com.poshanforlife.api.dto.CreateAssignmentRequest;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
@AdminOnly
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping
    public ApiResponse<List<AssignmentDto>> list(
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) UUID patientId) {
        return ApiResponse.ok(assignmentService.list(doctorId, patientId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentDto> create(@Valid @RequestBody CreateAssignmentRequest request) {
        return ApiResponse.ok(assignmentService.create(request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID id) {
        assignmentService.delete(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
