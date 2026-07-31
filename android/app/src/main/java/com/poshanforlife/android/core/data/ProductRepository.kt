package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CreateProductRequest
import com.poshanforlife.android.core.network.CreateProductSegmentRequest
import com.poshanforlife.android.core.network.ProductDto
import com.poshanforlife.android.core.network.ProductSegmentDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateProductRequest
import com.poshanforlife.android.core.network.UpdateProductSegmentRequest
import java.io.File

/** Physical goods (nutrition/supplements) — browse is open to all roles, every write is ADMIN-only server-side. */
interface ProductRepository {
    suspend fun listSegments(includeArchived: Boolean = false): Result<List<ProductSegmentDto>>
    suspend fun createSegment(request: CreateProductSegmentRequest): Result<ProductSegmentDto>
    suspend fun updateSegment(id: String, request: UpdateProductSegmentRequest): Result<ProductSegmentDto>

    /** Hard delete — blocked server-side (with a clear error) if the segment still has products. */
    suspend fun deleteSegment(id: String): Result<Unit>

    suspend fun list(segmentId: String? = null, search: String? = null): Result<List<ProductDto>>
    suspend fun get(id: String): Result<ProductDto>
    suspend fun create(request: CreateProductRequest): Result<ProductDto>
    suspend fun update(id: String, request: UpdateProductRequest): Result<ProductDto>
    suspend fun delete(id: String): Result<Unit>

    /** Appends the uploaded image, returning the product with its updated images list. mimeType must exactly match one the backend allowlists (image/jpeg|png|webp|gif) — a wildcard MIME type is rejected. */
    suspend fun uploadImage(id: String, file: File, mimeType: String): Result<ProductDto>
    suspend fun removeImage(id: String, url: String): Result<ProductDto>
}
