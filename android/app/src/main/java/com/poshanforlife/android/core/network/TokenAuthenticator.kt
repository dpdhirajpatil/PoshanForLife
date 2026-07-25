package com.poshanforlife.android.core.network

import com.poshanforlife.android.core.datastore.TokenDataStore
import com.poshanforlife.android.core.di.RefreshRetrofit
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On a 401, attempts POST /auth/refresh with the stored refresh token and
 * retries the original request once with the new access token. If refresh
 * also fails (or there's no refresh token), clears TokenDataStore —
 * AuthViewModel derives its state from TokenDataStore.currentUser() reactively,
 * so that alone is enough to drop the app back to the login screen, no
 * separate "check session" step needed anywhere else.
 *
 * Calls the refresh endpoint through @RefreshRetrofit — a Retrofit instance
 * built on a plain OkHttpClient with no AuthInterceptor/Authenticator of its
 * own — never through the main authenticated client. Otherwise a failing
 * refresh call would recurse back into this same Authenticator.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    @RefreshRetrofit private val refreshRetrofit: Retrofit,
) : Authenticator {

    private val refreshApi: AuthApi by lazy { refreshRetrofit.create(AuthApi::class.java) }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried once for this request chain, or this 401 IS the
        // refresh call itself — give up rather than loop.
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) return null

        val refreshToken = runBlocking { tokenDataStore.refreshToken().firstOrNull() }
        if (refreshToken.isNullOrBlank()) return null

        return runBlocking {
            try {
                val refreshResponse = refreshApi.refresh(RefreshRequest(refreshToken))
                val auth = refreshResponse.body()?.data
                if (refreshResponse.isSuccessful && auth != null) {
                    tokenDataStore.saveTokens(auth.accessToken, auth.refreshToken)
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${auth.accessToken}")
                        .build()
                } else {
                    tokenDataStore.clear()
                    null
                }
            } catch (e: Exception) {
                tokenDataStore.clear()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
