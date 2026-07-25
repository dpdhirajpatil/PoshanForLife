package com.poshanforlife.android.core.network

/**
 * What every repository returns instead of Retrofit's Response<T> — ViewModels
 * consume this and never touch Retrofit/OkHttp types directly.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val code: String, val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun <T> Result<T>.onError(action: (code: String, message: String) -> Unit): Result<T> {
    if (this is Result.Error) action(code, message)
    return this
}
