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
    /** Null for a phone-OTP account — see UserDto.email. */
    val email: String? = null,
    val role: String,
    val phone: String? = null,
    /** True once the phone has been proven by OTP — only then can it be used to sign in. */
    val phoneVerified: Boolean = false,
    val avatarUrl: String? = null,
)
