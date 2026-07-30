package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CatalogueApi
import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CreateCatalogueItemRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateCatalogueItemRequest
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class CatalogueRepositoryImpl @Inject constructor(
    private val catalogueApi: CatalogueApi,
    private val json: Json,
) : CatalogueRepository {

    override suspend fun list(type: String, status: String?, search: String?): Result<List<CatalogueItemDto>> =
        safeApiCall(json) { catalogueApi.list(type = type, status = status, search = search) }

    override suspend fun get(type: String, id: String): Result<CatalogueItemDto> =
        safeApiCall(json) { catalogueApi.get(type, id) }

    override suspend fun create(type: String, request: CreateCatalogueItemRequest): Result<CatalogueItemDto> =
        safeApiCall(json) { catalogueApi.create(type, request) }

    override suspend fun update(type: String, id: String, request: UpdateCatalogueItemRequest): Result<CatalogueItemDto> =
        safeApiCall(json) { catalogueApi.update(type, id, request) }

    override suspend fun uploadCoverImage(type: String, file: File): Result<String> {
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("image/*".toMediaType()),
        )
        return when (val result = safeApiCall(json) { catalogueApi.uploadCoverImage(type, filePart) }) {
            is Result.Success -> Result.Success(result.data.url)
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
    }
}
