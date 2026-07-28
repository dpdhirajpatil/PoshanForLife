package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** Bill-to reference — mirrors the backend's LeadRefDto/PatientRefDto (both same shape). */
@Serializable
data class DocumentPartyRefDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class DocumentUserRefDto(
    val id: String,
    val name: String,
)

/** documentType: "estimate" | "invoice". status: "draft" | "sent" | "paid". */
@Serializable
data class DocumentListItemDto(
    val id: String,
    val documentType: String = "invoice",
    val documentNumber: String,
    val status: String,
    val lead: DocumentPartyRefDto? = null,
    val patient: DocumentPartyRefDto? = null,
    val total: Double,
    val createdAt: String? = null,
)

@Serializable
data class DocumentItemDto(
    val itemName: String,
    val description: String? = null,
    val hsnSac: String? = null,
    val quantity: Int,
    val rateInr: Double,
    val lineTotal: Double,
)

@Serializable
data class DocumentDetailDto(
    val id: String,
    val documentType: String,
    val documentNumber: String,
    val status: String,
    val lead: DocumentPartyRefDto? = null,
    val patient: DocumentPartyRefDto? = null,
    val items: List<DocumentItemDto>,
    val discountInr: Double,
    val subtotal: Double,
    val cgstAmount: Double,
    val sgstAmount: Double,
    val total: Double,
    val notes: String? = null,
    val validForDays: Int? = null,
    val createdBy: DocumentUserRefDto? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class PdfUrlDto(val pdfUrl: String)

@Serializable
data class CreateDocumentItemRequest(
    val itemName: String,
    val description: String? = null,
    val hsnSac: String? = null,
    val quantity: Int,
    val rateInr: Double,
)

/** Exactly one of leadId/patientId must be set — enforced server-side (422 otherwise). */
@Serializable
data class CreateDocumentRequest(
    val documentType: String,
    val leadId: String? = null,
    val patientId: String? = null,
    val items: List<CreateDocumentItemRequest>,
    val notes: String? = null,
    val discountInr: Double? = null,
    val validForDays: Int? = null,
)

@Serializable
data class FromOrderRequest(val orderId: String)

@Serializable
data class UpdateDocumentStatusRequest(val status: String)
