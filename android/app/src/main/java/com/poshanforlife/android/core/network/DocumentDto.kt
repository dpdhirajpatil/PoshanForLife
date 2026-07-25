package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class DocumentListItemDto(
    val id: String,
    val documentNumber: String,
    val status: String,
    val total: Double,
)
