package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

/** phone/city/healthGoal are optional free text, stored on the linked Lead record server-side. */
@Serializable
data class SignupRequest(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val city: String? = null,
    val healthGoal: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthResponse(val accessToken: String, val refreshToken: String, val user: UserDto)

/** role is the raw wire string (e.g. "DOCTOR") — parse with Role.fromWire before branching on it. */
@Serializable
data class UserDto(val id: String, val name: String, val email: String, val role: String)
