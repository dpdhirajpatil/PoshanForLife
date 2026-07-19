package com.poshanforlife.api.dto;

import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;
import jakarta.validation.constraints.Size;

/**
 * Partial update — null fields are left unchanged; notes accepts "" to
 * clear. Transitioning paymentStatus to paid auto-creates an activation
 * transaction when the order has none yet.
 */
public record UpdateOrderRequest(
        PaymentStatus paymentStatus,
        OrderStatus status,
        @Size(max = 5000) String notes) {
}
