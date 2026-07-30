package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDetailDto

interface UserRepository {
    /**
     * Sent on login and from PoshanFirebaseMessagingService.onNewToken;
     * fire-and-forget — a failure here just means the next app open (or the
     * next token rotation) retries, never blocks the caller.
     */
    suspend fun updateFcmToken(userId: String, token: String): Result<UserDetailDto>

    /** Live server profile — used by AuthRepository.refreshCurrentUser() to detect a LEAD->PATIENT role promotion. */
    suspend fun getMe(): Result<UserDetailDto>
}
