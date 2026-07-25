package com.poshanforlife.android.core.di

import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.AuthRepositoryImpl
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.data.PatientRepositoryImpl
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.data.ReportRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
}
