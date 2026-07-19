package com.poshanforlife.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.poshanforlife.api.entity.OrderStatus;
import com.poshanforlife.api.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Full order with everything invoice rendering (prompt 08) needs: bill-to
 * patient, the originating assignment with its catalogue item + duration,
 * and the ledger entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetailDto(
        String id,
        BigDecimal amountInr,
        OrderStatus status,
        PaymentStatus paymentStatus,
        String notes,
        PatientRefDto patient,
        OrderProgrammeDto patientProgramme,
        List<TransactionSummaryDto> transactions,
        UserRefDto createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
