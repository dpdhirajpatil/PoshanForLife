package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.AuthResponse
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UserDto
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthResponse>
    suspend fun logout()

    /** Null once logged out (or before the first login). ViewModels observe this, never TokenDataStore directly. */
    fun currentUser(): Flow<UserDto?>
}
