package com.viperplayer.di

import com.viperplayer.data.account.AccountRepositoryImpl
import com.viperplayer.data.librarysync.LibrarySyncRepositoryImpl
import com.viperplayer.data.repository.AutoPlaylistRepositoryImpl
import com.viperplayer.data.repository.CacheRepositoryImpl
import com.viperplayer.data.repository.ListenTogetherRepositoryImpl
import com.viperplayer.data.source.AndroidTagDetailsReader
import com.viperplayer.data.repository.MediaLibraryRepositoryImpl
import com.viperplayer.data.repository.PlayerRepositoryImpl
import com.viperplayer.data.repository.PluginRepositoryImpl
import com.viperplayer.data.repository.RadioPlaylistRepositoryImpl
import com.viperplayer.data.repository.SearchRepositoryImpl
import com.viperplayer.data.repository.SettingsRepositoryImpl
import com.viperplayer.data.repository.ViperRepositoryImpl
import com.viperplayer.data.sync.push.LibraryPushOutbox
import com.viperplayer.data.sync.push.RoomLibraryPushOutbox
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.librarysync.LibrarySyncRepository
import com.viperplayer.domain.repository.AutoPlaylistRepository
import com.viperplayer.domain.repository.CacheRepository
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.RadioPlaylistRepository
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.TagDetailsReader
import com.viperplayer.domain.repository.ViperRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for data layer dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindLibrarySyncRepository(
        impl: LibrarySyncRepositoryImpl
    ): LibrarySyncRepository

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(
        impl: PlayerRepositoryImpl
    ): PlayerRepository

    @Binds
    @Singleton
    abstract fun bindPluginRepository(
        impl: PluginRepositoryImpl
    ): PluginRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        impl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindMediaLibraryRepository(
        impl: MediaLibraryRepositoryImpl
    ): MediaLibraryRepository

    @Binds
    @Singleton
    abstract fun bindAutoPlaylistRepository(
        impl: AutoPlaylistRepositoryImpl
    ): AutoPlaylistRepository

    @Binds
    @Singleton
    abstract fun bindRadioPlaylistRepository(
        impl: RadioPlaylistRepositoryImpl
    ): RadioPlaylistRepository

    @Binds
    @Singleton
    abstract fun bindCacheRepository(
        impl: CacheRepositoryImpl
    ): CacheRepository

    @Binds
    @Singleton
    abstract fun bindViperRepository(
        impl: ViperRepositoryImpl
    ): ViperRepository

    @Binds
    @Singleton
    abstract fun bindListenTogetherRepository(
        impl: ListenTogetherRepositoryImpl
    ): ListenTogetherRepository

    @Binds
    @Singleton
    abstract fun bindTagDetailsReader(
        impl: AndroidTagDetailsReader
    ): TagDetailsReader

    @Binds
    @Singleton
    abstract fun bindLibraryPushOutbox(
        impl: RoomLibraryPushOutbox
    ): LibraryPushOutbox
}

