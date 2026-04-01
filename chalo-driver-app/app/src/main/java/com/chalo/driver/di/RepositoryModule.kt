package com.chalo.driver.di

import com.chalo.driver.data.repository.AuthRepositoryImpl
import com.chalo.driver.data.repository.DriverRepositoryImpl
import com.chalo.driver.data.repository.RtdbRepositoryImpl
import com.chalo.driver.domain.repository.AuthRepository
import com.chalo.driver.domain.repository.DriverRepository
import com.chalo.driver.domain.repository.RtdbRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds @Singleton
    abstract fun bindDriverRepository(impl: DriverRepositoryImpl): DriverRepository

    @Binds @Singleton
    abstract fun bindRtdbRepository(impl: RtdbRepositoryImpl): RtdbRepository
}
