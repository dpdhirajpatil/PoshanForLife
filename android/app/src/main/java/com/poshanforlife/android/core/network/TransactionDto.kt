package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** catalogueType/serviceName are null for orphaned orders. */
@Serializable
data class TransactionListItemDto(
    val id: String,
    val transactionId: String,
    val invoiceNumber: String? = null,
    val transactionType: String,
    val paymentType: String,
    val patient: UserRefDto,
    val catalogueType: String? = null,
    val serviceName: String? = null,
    val amountInr: Double,
    val creditCharged: Double = 0.0,
    val createdBy: UserRefDto? = null,
    val createdAt: String,
)

/** Summary over the CURRENT FILTER SET (not just the current page). totalCreditConsumed is always non-negative. */
@Serializable
data class TransactionTotalsDto(
    val totalTransactionValue: Double,
    val totalCreditConsumed: Double,
)

@Serializable
data class TransactionListResponseDto(
    val transactions: List<TransactionListItemDto>,
    val summary: TransactionTotalsDto,
)

@Serializable
data class TransactionOrderDto(
    val id: String,
    val patientProgramme: OrderProgrammeDto? = null,
)

@Serializable
data class TransactionDetailDto(
    val id: String,
    val transactionId: String,
    val invoiceNumber: String? = null,
    val transactionType: String,
    val paymentType: String,
    val priceInr: Double,
    val discountInr: Double = 0.0,
    val amountInr: Double,
    val creditCharged: Double = 0.0,
    val source: String? = null,
    val paymentGatewayRef: String? = null,
    val notes: String? = null,
    val createdAt: String,
    val patient: PatientRefDto,
    val createdBy: UserRefDto? = null,
    val order: TransactionOrderDto,
)
