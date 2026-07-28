package com.poshanforlife.api.controller;

import com.poshanforlife.api.dto.ApiResponse;
import com.poshanforlife.api.dto.CreateDocumentRequest;
import com.poshanforlife.api.dto.DocumentDetailDto;
import com.poshanforlife.api.dto.DocumentListItemDto;
import com.poshanforlife.api.dto.FromOrderRequest;
import com.poshanforlife.api.dto.PdfUrlDto;
import com.poshanforlife.api.dto.UpdateDocumentStatusRequest;
import com.poshanforlife.api.security.AdminOrDoctor;
import com.poshanforlife.api.security.AdminOrDoctorOrPatient;
import com.poshanforlife.api.security.AuthenticatedUser;
import com.poshanforlife.api.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.UUID;

/**
 * Draft estimates and GST-aware invoices for the mobile app's RN-20 module —
 * extends prompt 08's Transaction/invoice-number machinery. Reads are
 * ADMIN+DOCTOR+PATIENT (PATIENT scoped server-side to their own documents,
 * 404 not 403 outside that scope); mutations are ADMIN+DOCTOR only, with
 * DOCTOR further scoped to their own leads/patients.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @AdminOrDoctorOrPatient
    public ApiResponse<List<DocumentListItemDto>> list(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID leadId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        Page<DocumentListItemDto> result = documentService.list(leadId, patientId, type, status,
                page, limit, caller);
        return ApiResponse.ok(result.getContent(), result.getTotalElements(), page, limit);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @AdminOrDoctor
    public ApiResponse<DocumentDetailDto> create(@Valid @RequestBody CreateDocumentRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(documentService.create(request, caller));
    }

    @PostMapping("/from-order")
    @ResponseStatus(HttpStatus.CREATED)
    @AdminOrDoctor
    public ApiResponse<DocumentDetailDto> fromOrder(@Valid @RequestBody FromOrderRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(documentService.fromOrder(request, caller));
    }

    @GetMapping("/{id}")
    @AdminOrDoctorOrPatient
    public ApiResponse<DocumentDetailDto> get(@PathVariable UUID id,
                                              @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(documentService.get(id, caller));
    }

    @PatchMapping("/{id}")
    @AdminOrDoctor
    public ApiResponse<DocumentDetailDto> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateDocumentStatusRequest request,
                                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(documentService.updateStatus(id, request, caller));
    }

    @GetMapping("/{id}/pdf")
    @AdminOrDoctorOrPatient
    public ApiResponse<PdfUrlDto> pdf(@PathVariable UUID id,
                                      @AuthenticationPrincipal AuthenticatedUser caller) {
        return ApiResponse.ok(documentService.getPdfUrl(id, caller));
    }
}
