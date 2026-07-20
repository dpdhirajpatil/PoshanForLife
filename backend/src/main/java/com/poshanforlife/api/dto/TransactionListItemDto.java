package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.PaymentType;
import com.poshanforlife.api.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/** Transactions list row. catalogueType/serviceName are null for orphaned orders. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionListItemDto(
        String id,
        String transactionId,
        String invoiceNumber,
        TransactionType transactionType,
        PaymentType paymentType,
        UserRefDto patient,
        CatalogueItemType catalogueType,
        String serviceName,
        BigDecimal amountInr,
        BigDecimal creditCharged,
        UserRefDto createdBy,
        Instant createdAt) {
}
