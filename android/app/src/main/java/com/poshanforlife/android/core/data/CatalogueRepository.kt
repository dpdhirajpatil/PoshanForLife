package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.CataloguePickerItemDto
import com.poshanforlife.android.core.network.Result

/** Minimal published-catalogue lookup for the lead-convert flow's service picker. */
interface CatalogueRepository {
    /** type is the plural URL path segment ("programmes"/"sessions"/"challenges"). */
    suspend fun listPublished(type: String, search: String? = null): Result<List<CataloguePickerItemDto>>
}
