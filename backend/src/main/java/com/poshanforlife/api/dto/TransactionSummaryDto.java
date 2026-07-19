package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/** Ledger entry as nested in order/assignment responses. */
public record TransactionSummaryDto(
        String id,
        String transactionId,
        String invoiceNumber,
        TransactionType transactionType,
        PaymentType paymentType,
        BigDecimal amountInr,
        Instant createdAt) {
}
