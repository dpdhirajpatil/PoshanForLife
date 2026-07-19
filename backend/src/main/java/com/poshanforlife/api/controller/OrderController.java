package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.OrderDetailDto;
import com.poshanforlife.api.dto.OrderListItemDto;
import com.poshanforlife.api.dto.UpdateOrderRequest;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Orders are created only as a side effect of the service-assignment flow —
 * matching the original API, there is deliberately no POST here.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@AdminOrDoctor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<List<OrderListItemDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        Page<OrderListItemDto> result = orderService.list(status, paymentStatus, search,
                dateFrom, dateTo, page, limit, caller);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailDto> get(@PathVariable UUID id,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(orderService.get(id, caller));
    }

    @PatchMapping("/{id}")
    public ApiResponse<OrderDetailDto> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateOrderRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(orderService.update(id, request, caller));
    }
}
