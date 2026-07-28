package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** serviceType/serviceName are null for orphaned orders (their catalogue item was deleted). */
@Serializable
data class OrderListItemDto(
    val id: String,
    val patient: UserRefDto,
    val serviceType: String? = null,
    val serviceName: String? = null,
    val amountInr: Double,
    val status: String,
    val paymentStatus: String,
    val createdAt: String,
)

/** The service-assignment context behind an order — nested in order & transaction detail alike. */
@Serializable
data class OrderProgrammeDto(
    val id: String,
    val serviceType: String? = null,
    val catalogueItem: ServiceRefDto? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String? = null,
    val assignedBy: UserRefDto? = null,
    val assignedDoctor: UserRefDto? = null,
)

@Serializable
data class OrderTransactionSummaryDto(
    val id: String,
    val transactionId: String,
    val invoiceNumber: String? = null,
    val transactionType: String,
    val paymentType: String,
    val amountInr: Double,
    val createdAt: String,
)

@Serializable
data class OrderDetailDto(
    val id: String,
    val amountInr: Double,
    val status: String,
    val paymentStatus: String,
    val notes: String? = null,
    val patient: PatientRefDto,
    val patientProgramme: OrderProgrammeDto? = null,
    val transactions: List<OrderTransactionSummaryDto> = emptyList(),
    val createdBy: UserRefDto? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** Partial update — omitted fields are left unchanged server-side. */
@Serializable
data class UpdateOrderRequest(
    val paymentStatus: String? = null,
    val status: String? = null,
    val notes: String? = null,
)
