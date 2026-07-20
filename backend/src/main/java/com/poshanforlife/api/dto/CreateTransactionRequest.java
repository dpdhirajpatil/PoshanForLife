package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Manual ledger entry (ADMIN) against an existing order. The same shape also
 * backs a future `source=mobile_app` payment-gateway-webhook variant — no
 * separate endpoint, just a different source/paymentGatewayRef.
 * priceInr and creditCharged are not client-supplied: priceInr is taken from
 * the order's face amount, creditCharged defaults to zero (no credit-wallet
 * feature yet).
 */
public record CreateTransactionRequest(
        @NotNull UUID orderId,
        @NotNull TransactionType transactionType,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal amountInr,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal discountInr,
        PaymentType paymentType,
        @Size(max = 255) String paymentGatewayRef,
        @Size(max = 5000) String notes,
        @Size(max = 32) String source) {
}
