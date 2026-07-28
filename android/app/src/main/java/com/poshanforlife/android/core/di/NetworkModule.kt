package com.poshanforlife.android.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.poshanforlife.android.BuildConfig
import com.poshanforlife.android.core.network.AppointmentApi
import com.poshanforlife.android.core.network.AuthApi
import com.poshanforlife.android.core.network.AuthInterceptor
import com.poshanforlife.android.core.network.DocumentApi
import com.poshanforlife.android.core.network.LeadApi
import com.poshanforlife.android.core.network.NotificationApi
import com.poshanforlife.android.core.network.OrderApi
import com.poshanforlife.android.core.network.PatientApi
import com.poshanforlife.android.core.network.ReportApi
import com.poshanforlife.android.core.network.TokenAuthenticator
import com.poshanforlife.android.core.network.TransactionApi
import com.poshanforlife.android.core.network.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .build()

    /** No AuthInterceptor/Authenticator — used only by TokenAuthenticator to call /auth/refresh itself. */
    @Provides
    @Singleton
    @RefreshHttpClient
    fun provideRefreshOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @RefreshRetrofit
    fun provideRefreshRetrofit(@RefreshHttpClient okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun providePatientApi(retrofit: Retrofit): PatientApi = retrofit.create(PatientApi::class.java)

    @Provides
    @Singleton
    fun provideDocumentApi(retrofit: Retrofit): DocumentApi = retrofit.create(DocumentApi::class.java)

    @Provides
    @Singleton
    fun provideLeadApi(retrofit: Retrofit): LeadApi = retrofit.create(LeadApi::class.java)

    @Provides
    @Singleton
    fun provideReportApi(retrofit: Retrofit): ReportApi = retrofit.create(ReportApi::class.java)

    @Provides
    @Singleton
    fun provideAppointmentApi(retrofit: Retrofit): AppointmentApi = retrofit.create(AppointmentApi::class.java)

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create(NotificationApi::class.java)

    @Provides
    @Singleton
    fun provideOrderApi(retrofit: Retrofit): OrderApi = retrofit.create(OrderApi::class.java)

    @Provides
    @Singleton
    fun provideTransactionApi(retrofit: Retrofit): TransactionApi = retrofit.create(TransactionApi::class.java)
}
