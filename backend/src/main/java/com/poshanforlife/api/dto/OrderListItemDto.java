package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.CatalogueItemType;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Orders list row. serviceType/serviceName are null for orphaned orders. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderListItemDto(
        String id,
        UserRefDto patient,
        CatalogueItemType serviceType,
        String serviceName,
        BigDecimal amountInr,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Instant createdAt) {
}
