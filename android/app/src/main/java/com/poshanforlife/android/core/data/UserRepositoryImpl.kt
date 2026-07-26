package com.poshanforlife.android.core.data

import com.poshanforlife.android.core.network.Result
import com.poshanforlife.android.core.network.UpdateUserRequest
import com.poshanforlife.android.core.network.UserApi
import com.poshanforlife.android.core.network.UserDetailDto
import com.poshanforlife.android.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApi,
    private val json: Json,
) : UserRepository {

    override suspend fun updateFcmToken(userId: String, token: String): Result<UserDetailDto> =
        safeApiCall(json) { userApi.update(userId, UpdateUserRequest(fcmToken = token)) }
}
