package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/** Physical goods (nutrition/supplements) — distinct from CatalogueItemDto's services. No purchase/cart fields yet, price is display-only. */
@Serializable
data class ProductDto(
    val id: String,
    val segmentId: String,
    val segmentName: String? = null,
    val name: String,
    val description: String? = null,
    val images: List<String> = emptyList(),
    val priceInr: Double? = null,
    val sku: String? = null,
    val status: String = "draft",
    val displayOrder: Int = 0,
)

@Serializable
data class ProductListResponseDto(val products: List<ProductDto> = emptyList())

/** displayOrder defaults to "append to end" server-side when omitted. */
@Serializable
data class CreateProductRequest(
    val segmentId: String,
    val name: String,
    val description: String? = null,
    val priceInr: Double? = null,
    val sku: String? = null,
    val status: String? = null,
    val displayOrder: Int? = null,
)

/** Partial update — every field optional, only non-null ones are applied server-side. Images aren't settable here — see ProductApi.uploadImage/removeImage. */
@Serializable
data class UpdateProductRequest(
    val segmentId: String? = null,
    val name: String? = null,
    val description: String? = null,
    val priceInr: Double? = null,
    val sku: String? = null,
    val status: String? = null,
    val displayOrder: Int? = null,
)

/** Admin-manageable product category — not a fixed enum, so the lineup can grow without a code change. */
@Serializable
data class ProductSegmentDto(
    val id: String,
    val name: String,
    val displayOrder: Int = 0,
    val status: String = "active",
    val publishedProductCount: Long = 0,
)

@Serializable
data class CreateProductSegmentRequest(val name: String, val displayOrder: Int? = null)

/** Partial update — every field optional. */
@Serializable
data class UpdateProductSegmentRequest(
    val name: String? = null,
    val displayOrder: Int? = null,
    val status: String? = null,
)
