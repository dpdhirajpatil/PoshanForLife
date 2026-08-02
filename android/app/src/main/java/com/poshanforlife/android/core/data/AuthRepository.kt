package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.AuthResponse
import com.poshanforlife.android.core.network.OtpPurpose
import com.poshanforlife.android.core.network.OtpVerifyResponse
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.SignupRequest
import com.poshanforlife.android.core.network.UserDto
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthResponse>

    /** Public self-signup — same AuthResponse shape as login, lands the caller in role LEAD. */
    suspend fun signup(request: SignupRequest): Result<AuthResponse>

    /** Sends a one-time code. The backend rejects a purpose that doesn't fit the number's state (already registered / unknown / owned by someone else). */
    suspend fun requestOtp(phone: String, purpose: OtpPurpose): Result<Unit>

    /**
     * Verifies a code and completes its purpose. SIGNUP/LOGIN return tokens and
     * are persisted here exactly like [login]; ADD_PHONE returns null tokens and
     * only refreshes the cached profile, leaving the current session intact.
     */
    suspend fun verifyOtp(
        phone: String,
        otp: String,
        purpose: OtpPurpose,
        name: String? = null,
    ): Result<OtpVerifyResponse>

    suspend fun logout()

    /** Null once logged out (or before the first login). ViewModels observe this, never TokenDataStore directly. */
    fun currentUser(): Flow<UserDto?>

    /**
     * Re-fetches GET /users/me and overwrites the cached user — used to detect
     * a staff-side LEAD->PATIENT conversion (AN-14's convert flow) on app
     * resume, since nothing else pushes that change to the device.
     */
    suspend fun refreshCurrentUser(): Result<Unit>
}
