package com.chalo.customer.di

import android.content.Context
import androidx.room.Room
import com.chalo.customer.data.local.AppDatabase
import com.chalo.customer.data.local.DatabaseMigrations
import com.chalo.customer.data.local.dao.NotificationDao
import com.chalo.customer.data.local.dao.RideDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "chalo.db")
            .addMigrations(*DatabaseMigrations.ALL)
            .build()

    @Provides
    fun provideRideDao(db: AppDatabase): RideDao = db.rideDao()

    @Provides
    fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
}
