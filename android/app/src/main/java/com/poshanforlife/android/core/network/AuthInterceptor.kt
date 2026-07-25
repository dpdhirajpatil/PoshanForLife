package com.poshanforlife.android.core.network

import com.poshanforlife.android.core.datastore.TokenDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Attaches `Authorization: Bearer <token>` to every request, when a token is stored. */
class AuthInterceptor @Inject constructor(
    private val tokenDataStore: TokenDataStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = runBlocking { tokenDataStore.accessToken().firstOrNull() }
        val request = chain.request().let { original ->
            if (accessToken.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
