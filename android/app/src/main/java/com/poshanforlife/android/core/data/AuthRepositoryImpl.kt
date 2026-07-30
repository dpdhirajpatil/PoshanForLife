package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.datastore.TokenDataStore
import com.poshanforlife.android.core.network.AuthApi
import com.poshanforlife.android.core.network.AuthResponse
import com.poshanforlife.android.core.network.LoginRequest
import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.SignupRequest
import com.poshanforlife.android.core.network.UserDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val userRepository: UserRepository,
    private val tokenDataStore: TokenDataStore,
    private val json: Json,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<AuthResponse> {
        val result = safeApiCall(json) { authApi.login(LoginRequest(email, password)) }
        if (result is Result.Success) {
            tokenDataStore.saveTokens(result.data.accessToken, result.data.refreshToken)
            tokenDataStore.saveUser(result.data.user)
        }
        return result
    }

    override suspend fun signup(request: SignupRequest): Result<AuthResponse> {
        val result = safeApiCall(json) { authApi.signup(request) }
        if (result is Result.Success) {
            tokenDataStore.saveTokens(result.data.accessToken, result.data.refreshToken)
            tokenDataStore.saveUser(result.data.user)
        }
        return result
    }

    override suspend fun logout() {
        tokenDataStore.clear()
    }

    override fun currentUser(): Flow<UserDto?> = tokenDataStore.currentUser()

    override suspend fun refreshCurrentUser(): Result<Unit> =
        when (val result = userRepository.getMe()) {
            is Result.Success -> {
                val detail = result.data
                tokenDataStore.saveUser(UserDto(detail.id, detail.name, detail.email, detail.role))
                Result.Success(Unit)
            }
            is Result.Error -> result
            Result.Loading -> Result.Loading
        }
}
