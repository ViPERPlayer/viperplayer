package com.viperplayer.di

import com.viperplayer.BuildConfig
import com.viperplayer.data.account.AccountBackendConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the parsed ViPER account backend gRPC endpoint. Null when `VIPER_BACKEND_GRPC` holds the
 * build placeholder / is blank / is malformed — in which case the account feature is not configured
 * (the gRPC client reports `isConfigured == false` and the UI stays gated).
 */
@Module
@InstallIn(SingletonComponent::class)
object AccountModule {

    @Provides
    @Singleton
    fun provideAccountBackendConfig(): AccountBackendConfig? =
        AccountBackendConfig.parse(BuildConfig.VIPER_BACKEND_GRPC)
}
