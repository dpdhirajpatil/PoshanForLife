package com.poshanforlife.android.core.di

import android.content.Context
import androidx.room.Room
import com.poshanforlife.android.core.data.local.AppDatabase
import com.poshanforlife.android.core.data.local.HealthEntryDao
import com.poshanforlife.android.core.data.local.MedicationReminderDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "poshan.db").build()

    @Provides
    fun provideHealthEntryDao(db: AppDatabase): HealthEntryDao = db.healthEntryDao()

    @Provides
    fun provideMedicationReminderDao(db: AppDatabase): MedicationReminderDao = db.medicationReminderDao()
}
