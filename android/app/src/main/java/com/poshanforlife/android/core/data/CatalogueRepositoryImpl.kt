package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CataloguePickerItemDto
import com.poshanforlife.android.core.network.CatalogueApi
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class CatalogueRepositoryImpl @Inject constructor(
    private val catalogueApi: CatalogueApi,
    private val json: Json,
) : CatalogueRepository {

    override suspend fun listPublished(type: String, search: String?): Result<List<CataloguePickerItemDto>> =
        safeApiCall(json) { catalogueApi.list(type = type, search = search) }
}
