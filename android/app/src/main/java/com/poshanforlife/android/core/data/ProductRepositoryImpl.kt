package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CreateProductRequest
import com.poshanforlife.android.core.network.CreateProductSegmentRequest
import com.poshanforlife.android.core.network.ProductApi
import com.poshanforlife.android.core.network.ProductDto
import com.poshanforlife.android.core.network.ProductSegmentDto
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateProductRequest
import com.poshanforlife.android.core.network.UpdateProductSegmentRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val productApi: ProductApi,
    private val json: Json,
) : ProductRepository {

    override suspend fun listSegments(includeArchived: Boolean): Result<List<ProductSegmentDto>> =
        safeApiCall(json) { productApi.listSegments(includeArchived) }

    override suspend fun createSegment(request: CreateProductSegmentRequest): Result<ProductSegmentDto> =
        safeApiCall(json) { productApi.createSegment(request) }

    override suspend fun updateSegment(id: String, request: UpdateProductSegmentRequest): Result<ProductSegmentDto> =
        safeApiCall(json) { productApi.updateSegment(id, request) }

    override suspend fun deleteSegment(id: String): Result<Unit> {
        return when (val result = safeApiCall(json) { productApi.deleteSegment(id) }) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun list(segmentId: String?, search: String?): Result<List<ProductDto>> {
        val result = safeApiCall(json) { productApi.list(segmentId = segmentId, search = search) }
        return when (result) {
            is Result.Success -> Result.Success(result.data.products)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun get(id: String): Result<ProductDto> =
        safeApiCall(json) { productApi.get(id) }

    override suspend fun create(request: CreateProductRequest): Result<ProductDto> =
        safeApiCall(json) { productApi.create(request) }

    override suspend fun update(id: String, request: UpdateProductRequest): Result<ProductDto> =
        safeApiCall(json) { productApi.update(id, request) }

    override suspend fun delete(id: String): Result<Unit> {
        return when (val result = safeApiCall(json) { productApi.delete(id) }) {
            is Result.Success -> Result.Success(Unit)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }

    override suspend fun uploadImage(id: String, file: File, mimeType: String): Result<ProductDto> {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(mimeType.toMediaType()),
        )
        return safeApiCall(json) { productApi.uploadImage(id, filePart) }
    }

    override suspend fun removeImage(id: String, url: String): Result<ProductDto> =
        safeApiCall(json) { productApi.removeImage(id, url) }
}
