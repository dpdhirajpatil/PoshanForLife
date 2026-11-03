package com.poshanforlife.android.core.datastore

/**
 * The slice of token storage [com.poshanforlife.android.core.network.TokenAuthenticator]
 * needs. Implemented by [TokenDataStore]; exists as a seam so the authenticator's
 * refresh logic can be unit-tested on the JVM without Android's DataStore.
 */
interface SessionTokenStore {
    suspend fun currentAccessToken(): String?
    suspend fun currentRefreshToken(): String?
    suspend fun saveTokens(access: String, refresh: String)
    suspend fun clear()
}
