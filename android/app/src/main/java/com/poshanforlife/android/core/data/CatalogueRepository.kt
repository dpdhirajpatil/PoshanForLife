package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CatalogueItemDto
import com.poshanforlife.android.core.network.CreateCatalogueItemRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateCatalogueItemRequest
import java.io.File

/** Programmes/Sessions/Challenges catalogue — browse (all roles) + CRUD (ADMIN-only server-side). */
interface CatalogueRepository {
    /** type is the plural URL path segment ("programmes"/"sessions"/"challenges"). */
    suspend fun list(type: String, status: String? = null, search: String? = null): Result<List<CatalogueItemDto>>

    suspend fun get(type: String, id: String): Result<CatalogueItemDto>

    suspend fun create(type: String, request: CreateCatalogueItemRequest): Result<CatalogueItemDto>

    suspend fun update(type: String, id: String, request: UpdateCatalogueItemRequest): Result<CatalogueItemDto>

    /** Uploads the given local file, returning the resulting cover image URL. */
    suspend fun uploadCoverImage(type: String, file: File): Result<String>
}
