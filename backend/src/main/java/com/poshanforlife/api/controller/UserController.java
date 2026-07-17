package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.AssignPatientsRequest;
import com.poshanforlife.api.dto.ChangePasswordRequest;
import com.poshanforlife.api.dto.CreateUserRequest;
import com.poshanforlife.api.dto.NotificationPrefsRequest;
import com.poshanforlife.api.dto.UpdateUserRequest;
import com.poshanforlife.api.dto.UserDetailDto;
import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.UserService;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @AdminOnly
    public ApiResponse<List<UserDetailDto>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        Page<UserDetailDto> result = userService.list(role, search, page, limit);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDetailDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDetailDto> get(@PathVariable UUID id,
                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(userService.get(id, caller));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserDetailDto> update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateUserRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(userService.update(id, request, caller));
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    public ApiResponse<Map<String, Boolean>> softDelete(@PathVariable UUID id) {
        userService.softDelete(id);
        return ApiResponse.ok(Map.of("deactivated", true));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Map<String, Boolean>> changePassword(@PathVariable UUID id,
                                                            @Valid @RequestBody ChangePasswordRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser caller) {
        userService.changePassword(id, request, caller);
        return ApiResponse.ok(Map.of("changed", true));
    }

    @PostMapping("/{id}/assign-patients")
    @AdminOnly
    public ApiResponse<List<UserDetailDto>> assignPatients(@PathVariable UUID id,
                                                           @Valid @RequestBody AssignPatientsRequest request) {
        return ApiResponse.ok(userService.assignPatients(id, request));
    }

    /** Read counterpart of assign-patients so the UI can pre-select current assignments. */
    @GetMapping("/{id}/patients")
    @AdminOnly
    public ApiResponse<List<UserDetailDto>> assignedPatients(@PathVariable UUID id) {
        return ApiResponse.ok(userService.getAssignedPatients(id));
    }

    @PatchMapping("/{id}/notification-prefs")
    public ApiResponse<UserDetailDto> updateNotificationPrefs(@PathVariable UUID id,
                                                              @Valid @RequestBody NotificationPrefsRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(userService.updateNotificationPrefs(id, request, caller));
    }
}
