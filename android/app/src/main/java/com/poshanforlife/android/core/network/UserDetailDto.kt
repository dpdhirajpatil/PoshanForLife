package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * GET /users/me's richer profile shape — distinct from the leaner UserDto
 * embedded in AuthResponse (id/name/email/role only). Only the fields the
 * dashboard needs are modeled; ignoreUnknownKeys covers the rest.
 */
@Serializable
data class UserDetailDto(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val phone: String? = null,
    val avatarUrl: String? = null,
)
