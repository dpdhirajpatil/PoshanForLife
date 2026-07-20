package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreateTransactionRequest;
import com.poshanforlife.api.dto.TransactionDetailDto;
import com.poshanforlife.api.dto.TransactionListResponseDto;
import com.poshanforlife.api.security.AdminOnly;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The financial ledger. GET is ADMIN+DOCTOR (DOCTOR scoped server-side);
 * POST (manual entry, and the documented source=mobile_app webhook variant)
 * is ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@AdminOrDoctor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ApiResponse<TransactionListResponseDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String catalogue,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        TransactionService.TransactionListResult result = transactionService.list(search, userId,
                catalogue, paymentType, dateFrom, dateTo, page, limit, caller);
        TransactionListResponseDto body = new TransactionListResponseDto(
                result.page().getContent(), result.summary());
        return ApiResponse.ok(body, result.page().getTotalElements(), page, limit);
    }

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionDetailDto> create(@Valid @RequestBody CreateTransactionRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(transactionService.create(request, caller));
    }

    @GetMapping("/{id}")
    public ApiResponse<TransactionDetailDto> get(@PathVariable UUID id,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(transactionService.get(id, caller));
    }
}
