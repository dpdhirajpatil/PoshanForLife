package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Full transaction with everything invoice rendering needs. Mirrors the
 * original API's shape (patient/createdBy/order→patientProgramme→catalogue
 * item/assignedBy/dates) — the nested catalogue item uses the shared
 * ServiceRefDto/OrderProgrammeDto pattern established in prompts 06/07
 * (one programme/session/challenge reference field, not three parallel
 * ones) rather than the original's three separate keys.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionDetailDto(
        String id,
        String transactionId,
        String invoiceNumber,
        TransactionType transactionType,
        PaymentType paymentType,
        BigDecimal priceInr,
        BigDecimal discountInr,
        BigDecimal amountInr,
        BigDecimal creditCharged,
        String source,
        String paymentGatewayRef,
        String notes,
        Instant createdAt,
        PatientRefDto patient,
        UserRefDto createdBy,
        TransactionOrderDto order) {
}
