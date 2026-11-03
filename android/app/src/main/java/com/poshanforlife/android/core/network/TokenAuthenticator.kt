package com.poshanforlife.android.core.network

import com.poshanforlife.android.core.datastore.SessionTokenStore
import com.poshanforlife.android.core.datastore.TokenDataStore
import com.poshanforlife.android.core.di.RefreshRetrofit
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
 * also fails (or there's no refresh token), clears the stored session —
 * AuthViewModel derives its state from TokenDataStore.currentUser() reactively,
 * so that alone is enough to drop the app back to the login screen, no
 * separate "check session" step needed anywhere else.
 *
 * Refreshes are serialized. The backend rotates the refresh token on every
 * use, so it only works once: when several requests expire together (e.g. the
 * patient dashboard loading its cards in parallel), letting each one refresh
 * would spend the token on the first call, get the rest rejected, and log the
 * user out. Instead the first request to take the lock refreshes, and every
 * request queued behind it sees that the stored access token is no longer the
 * one it failed with and simply retries with the new one.
 *
 * Calls the refresh endpoint through @RefreshRetrofit — a Retrofit instance
 * built on a plain OkHttpClient with no AuthInterceptor/Authenticator of its
 * own — never through the main authenticated client. Otherwise a failing
 * refresh call would recurse back into this same Authenticator.
 */
@Singleton
class TokenAuthenticator(
    private val tokenStore: SessionTokenStore,
    refreshApiProvider: () -> AuthApi,
) : Authenticator {

    @Inject
    constructor(
        tokenDataStore: TokenDataStore,
        @RefreshRetrofit refreshRetrofit: Retrofit,
    ) : this(tokenDataStore, { refreshRetrofit.create(AuthApi::class.java) })

    private val refreshApi: AuthApi by lazy(refreshApiProvider)
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried once for this request chain, or this 401 IS the
        // refresh call itself — give up rather than loop.
        if (responseCount(response) >= 2) return null
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) return null

        val failedAccessToken = response.request.header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)

        synchronized(refreshLock) {
            // Another request refreshed while this one waited for the lock:
            // reuse its token rather than spending the (already rotated)
            // refresh token a second time.
            val currentAccessToken = runBlocking { tokenStore.currentAccessToken() }
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != failedAccessToken) {
                return response.request.withBearer(currentAccessToken)
            }

            val refreshToken = runBlocking { tokenStore.currentRefreshToken() }
            if (refreshToken.isNullOrBlank()) return null

            return runBlocking {
                try {
                    val refreshResponse = refreshApi.refresh(RefreshRequest(refreshToken))
                    val auth = refreshResponse.body()?.data
                    if (refreshResponse.isSuccessful && auth != null) {
                        tokenStore.saveTokens(auth.accessToken, auth.refreshToken)
                        response.request.withBearer(auth.accessToken)
                    } else {
                        tokenStore.clear()
                        null
                    }
                } catch (e: Exception) {
                    tokenStore.clear()
                    null
                }
            }
        }
    }

    private fun Request.withBearer(accessToken: String): Request =
        newBuilder().header(AUTHORIZATION, "$BEARER_PREFIX$accessToken").build()

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
