package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

/** The order created alongside a service assignment, with its ledger entries. */
public record OrderSummaryDto(
        String id,
        BigDecimal amountInr,
        OrderStatus status,
        PaymentStatus paymentStatus,
        List<TransactionSummaryDto> transactions) {
}
