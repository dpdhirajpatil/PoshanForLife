package com.poshanforlife.android.core.network

import com.poshanforlife.android.core.datastore.SessionTokenStore
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import retrofit2.Response as RetrofitResponse

class TokenAuthenticatorTest {

    @Test
    fun `parallel 401s share one refresh and all retry with the new token`() {
        val tokens = FakeTokenStore(access = "access-1", refresh = "refresh-1")
        val api = RotatingAuthApi(validRefreshToken = "refresh-1", refreshDelayMs = 200)
        val authenticator = TokenAuthenticator(tokens) { api }
        val parallelRequests = 5
        val barrier = CyclicBarrier(parallelRequests)
        val pool = Executors.newFixedThreadPool(parallelRequests)

        val results = (1..parallelRequests).map {
            pool.submit<Request?> {
                barrier.await()
                authenticator.authenticate(null, unauthorized(failedWith = "access-1"))
            }
        }.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals("only one refresh call may spend the rotating token", 1, api.refreshCalls.get())
        results.forEach { assertEquals("Bearer access-2", it?.header("Authorization")) }
        assertEquals("access-2", tokens.access)
        assertEquals("refresh-2", tokens.refresh)
        assertEquals("session must survive", 0, tokens.clearCalls.get())
    }

    @Test
    fun `request that failed with a stale token reuses the stored one without refreshing`() {
        val tokens = FakeTokenStore(access = "access-2", refresh = "refresh-2")
        val api = RotatingAuthApi(validRefreshToken = "refresh-2")
        val authenticator = TokenAuthenticator(tokens) { api }

        val retry = authenticator.authenticate(null, unauthorized(failedWith = "access-1"))

        assertEquals("Bearer access-2", retry?.header("Authorization"))
        assertEquals(0, api.refreshCalls.get())
    }

    @Test
    fun `rejected refresh clears the session and gives up`() {
        val tokens = FakeTokenStore(access = "access-1", refresh = "revoked")
        val api = RotatingAuthApi(validRefreshToken = "something-else")
        val authenticator = TokenAuthenticator(tokens) { api }

        val retry = authenticator.authenticate(null, unauthorized(failedWith = "access-1"))

        assertNull(retry)
        assertEquals(1, tokens.clearCalls.get())
        assertNull(tokens.access)
    }

    @Test
    fun `gives up after one retry instead of looping`() {
        val tokens = FakeTokenStore(access = "access-1", refresh = "refresh-1")
        val api = RotatingAuthApi(validRefreshToken = "refresh-1")
        val authenticator = TokenAuthenticator(tokens) { api }
        val firstAttempt = unauthorized(failedWith = "access-0")
        val retriedAttempt = unauthorized(failedWith = "access-1").newBuilder()
            .priorResponse(firstAttempt)
            .build()

        assertNull(authenticator.authenticate(null, retriedAttempt))
        assertEquals(0, api.refreshCalls.get())
    }

    @Test
    fun `a 401 from the refresh endpoint itself is not retried`() {
        val tokens = FakeTokenStore(access = "access-1", refresh = "refresh-1")
        val api = RotatingAuthApi(validRefreshToken = "refresh-1")
        val authenticator = TokenAuthenticator(tokens) { api }

        assertNull(authenticator.authenticate(null, unauthorized(path = "/api/v1/auth/refresh")))
        assertEquals(0, api.refreshCalls.get())
    }

    private fun unauthorized(
        failedWith: String? = null,
        path: String = "/api/v1/users/me",
    ): okhttp3.Response {
        val request = Request.Builder()
            .url("http://localhost$path")
            .apply { if (failedWith != null) header("Authorization", "Bearer $failedWith") }
            .build()
        return okhttp3.Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    /** In-memory stand-in for TokenDataStore. */
    private class FakeTokenStore(access: String?, refresh: String?) : SessionTokenStore {
        @Volatile var access: String? = access
        @Volatile var refresh: String? = refresh
        val clearCalls = AtomicInteger()

        override suspend fun currentAccessToken() = access
        override suspend fun currentRefreshToken() = refresh
        override suspend fun saveTokens(access: String, refresh: String) {
            this.access = access
            this.refresh = refresh
        }
        override suspend fun clear() {
            clearCalls.incrementAndGet()
            access = null
            refresh = null
        }
    }

    /**
     * Mimics the backend's rotation: a refresh token works exactly once, and
     * each success issues the next access/refresh pair.
     */
    private class RotatingAuthApi(
        validRefreshToken: String,
        private val refreshDelayMs: Long = 0,
    ) : AuthApi {
        val refreshCalls = AtomicInteger()
        private var valid: String? = validRefreshToken
        private var generation = 1

        override suspend fun refresh(body: RefreshRequest): RetrofitResponse<ApiResponse<AuthResponse>> {
            refreshCalls.incrementAndGet()
            delay(refreshDelayMs) // widens the race window, like a real network call
            synchronized(this) {
                if (body.refreshToken != valid) {
                    return RetrofitResponse.error(
                        401,
                        """{"success":false,"error":"Invalid refresh token","code":"AUTH_REQUIRED"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                }
                generation++
                valid = "refresh-$generation"
                return RetrofitResponse.success(
                    ApiResponse(
                        success = true,
                        data = AuthResponse(
                            accessToken = "access-$generation",
                            refreshToken = "refresh-$generation",
                            user = UserDto(id = "u1", name = "Test", role = "PATIENT"),
                        ),
                    ),
                )
            }
        }

        override suspend fun login(body: LoginRequest) = throw UnsupportedOperationException()
        override suspend fun signup(body: SignupRequest) = throw UnsupportedOperationException()
    }
}
