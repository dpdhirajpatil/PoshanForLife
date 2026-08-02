package com.poshanforlife.android.core.network

import kotlinx.serialization.Serializable

/**
 * What an OTP is being issued for. The wire values are the uppercase names the
 * backend's OtpPurpose enum uses; it parses case-insensitively, but send them
 * uppercase to match the API docs.
 */
enum class OtpPurpose(val wire: String) {
    /** Create a new LEAD account owning this phone. */
    SIGNUP("SIGNUP"),
    /** Authenticate an account that already verified this phone. */
    LOGIN("LOGIN"),
    /** Attach a phone to the already-signed-in caller. */
    ADD_PHONE("ADD_PHONE"),
}

@Serializable
data class OtpRequestBody(val phone: String, val purpose: String)

/** name is required by the backend for SIGNUP only, and ignored for the other purposes. */
@Serializable
data class OtpVerifyBody(
    val phone: String,
    val otp: String,
    val purpose: String,
    val name: String? = null,
)

/** The code itself is never returned, in any environment — only how long it lasts. */
@Serializable
data class OtpRequestResponse(val sent: Boolean = true, val expiresInSeconds: Long = 600)

/**
 * Same shape as [AuthResponse] but with nullable tokens: ADD_PHONE returns the
 * updated profile and no tokens, because the caller is already signed in and
 * their existing token stays valid. Kept as its own type so [AuthResponse] —
 * used by login/signup/refresh, which always carry tokens — stays non-null.
 */
@Serializable
data class OtpVerifyResponse(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: UserDto,
)
