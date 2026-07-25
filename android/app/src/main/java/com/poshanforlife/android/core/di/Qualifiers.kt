package com.poshanforlife.android.core.di

import javax.inject.Qualifier

/** The unauthenticated OkHttpClient/Retrofit pair used only to call POST /auth/refresh. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RefreshRetrofit
