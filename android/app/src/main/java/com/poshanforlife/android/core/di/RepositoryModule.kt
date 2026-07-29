package com.poshanforlife.android.core.di

import com.poshanforlife.android.core.data.AppointmentRepository
import com.poshanforlife.android.core.data.AppointmentRepositoryImpl
import com.poshanforlife.android.core.data.AuthRepository
import com.poshanforlife.android.core.data.AuthRepositoryImpl
import com.poshanforlife.android.core.data.CatalogueRepository
import com.poshanforlife.android.core.data.CatalogueRepositoryImpl
import com.poshanforlife.android.core.data.DocumentRepository
import com.poshanforlife.android.core.data.DocumentRepositoryImpl
import com.poshanforlife.android.core.data.HealthRecordRepository
import com.poshanforlife.android.core.data.HealthRecordRepositoryImpl
import com.poshanforlife.android.core.data.LeadRepository
import com.poshanforlife.android.core.data.LeadRepositoryImpl
import com.poshanforlife.android.core.data.NotificationRepository
import com.poshanforlife.android.core.data.NotificationRepositoryImpl
import com.poshanforlife.android.core.data.OrderRepository
import com.poshanforlife.android.core.data.OrderRepositoryImpl
import com.poshanforlife.android.core.data.PatientRepository
import com.poshanforlife.android.core.data.PatientRepositoryImpl
import com.poshanforlife.android.core.data.ReportRepository
import com.poshanforlife.android.core.data.ReportRepositoryImpl
import com.poshanforlife.android.core.data.TransactionRepository
import com.poshanforlife.android.core.data.TransactionRepositoryImpl
import com.poshanforlife.android.core.data.UserRepository
import com.poshanforlife.android.core.data.UserRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindAppointmentRepository(impl: AppointmentRepositoryImpl): AppointmentRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindOrderRepository(impl: OrderRepositoryImpl): OrderRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindHealthRecordRepository(impl: HealthRecordRepositoryImpl): HealthRecordRepository

    @Binds
    @Singleton
    abstract fun bindLeadRepository(impl: LeadRepositoryImpl): LeadRepository

    @Binds
    @Singleton
    abstract fun bindCatalogueRepository(impl: CatalogueRepositoryImpl): CatalogueRepository
}
