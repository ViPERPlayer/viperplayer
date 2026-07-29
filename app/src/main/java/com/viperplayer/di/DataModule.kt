package com.viperplayer.di

import com.viperplayer.data.account.AccountRepositoryImpl
import com.viperplayer.data.download.AutoDownloader
import com.viperplayer.data.download.DownloadManager
import com.viperplayer.data.librarysync.LibrarySyncRepositoryImpl
import com.viperplayer.data.repository.AutoPlaylistRepositoryImpl
import com.viperplayer.data.repository.CacheRepositoryImpl
import com.viperplayer.data.repository.LyricsPreferencesRepositoryImpl
import com.viperplayer.data.repository.MockListenTogetherRepositoryImpl
import com.viperplayer.data.repository.RealListenTogetherRepositoryImpl
import com.viperplayer.data.social.DeviceIdStore
import com.viperplayer.data.social.KtorJamSocketClient
import com.viperplayer.data.social.SessionApi
import com.viperplayer.data.source.AndroidTagDetailsReader
import com.viperplayer.data.repository.MediaLibraryRepositoryImpl
import com.viperplayer.data.repository.PlayerRepositoryImpl
import com.viperplayer.data.repository.PluginRepositoryImpl
import com.viperplayer.data.rec.IndexingStatusProvider
import com.viperplayer.data.rec.RecommendationIndexRepository
import com.viperplayer.data.repository.RadioPlaylistRepositoryImpl
import com.viperplayer.data.repository.RecommendationRepositoryImpl
import com.viperplayer.data.repository.SearchRepositoryImpl
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.data.repository.SettingsRepositoryImpl
import com.viperplayer.data.repository.ViperRepositoryImpl
import com.viperplayer.data.sync.LibrarySync
import com.viperplayer.data.sync.LibrarySyncManager
import com.viperplayer.data.sync.push.LibraryPushOutbox
import com.viperplayer.data.sync.push.RoomLibraryPushOutbox
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.librarysync.LibrarySyncRepository
import com.viperplayer.domain.repository.AutoPlaylistRepository
import com.viperplayer.domain.repository.CacheRepository
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.LyricsPreferencesRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.RadioPlaylistRepository
import com.viperplayer.domain.repository.RecommendationRepository
import com.viperplayer.domain.repository.SearchRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.TagDetailsReader
import com.viperplayer.domain.repository.ViperRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
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
    abstract fun bindLibrarySync(
        impl: LibrarySyncManager
    ): LibrarySync

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
    abstract fun bindRecommendationRepository(
        impl: RecommendationRepositoryImpl
    ): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindIndexingStatusProvider(
        impl: RecommendationIndexRepository
    ): IndexingStatusProvider

    @Binds
    @Singleton
    abstract fun bindLyricsPreferencesRepository(
        impl: LyricsPreferencesRepositoryImpl
    ): LyricsPreferencesRepository

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
    abstract fun bindTagDetailsReader(
        impl: AndroidTagDetailsReader
    ): TagDetailsReader

    @Binds
    @Singleton
    abstract fun bindLibraryPushOutbox(
        impl: RoomLibraryPushOutbox
    ): LibraryPushOutbox

    /** Auto-download-on-like seam: the media library repo enqueues via this, backed by DownloadManager. */
    @Binds
    @Singleton
    abstract fun bindAutoDownloader(
        impl: DownloadManager
    ): AutoDownloader

    companion object {
        /**
         * Picks the Listen-Together backend at wiring time: the REAL backend-backed implementation when
         * a `VIPER_BACKEND_URL` is configured, otherwise the in-memory [MockListenTogetherRepositoryImpl]
         * so the feature still demos with no server. `@Provides` (not `@Binds`) because the choice is a
         * runtime branch, not a fixed type binding.
         */
        @Provides
        @Singleton
        fun provideListenTogetherRepository(
            sessionApi: SessionApi,
            socketClient: KtorJamSocketClient,
            deviceIdStore: DeviceIdStore,
            accountRepository: AccountRepository,
            stringProvider: StringProvider,
            mock: MockListenTogetherRepositoryImpl,
        ): ListenTogetherRepository =
            if (sessionApi.isConfigured) {
                RealListenTogetherRepositoryImpl(
                    sessionApi = sessionApi,
                    socketClient = socketClient,
                    deviceIdStore = deviceIdStore,
                    accountRepository = accountRepository,
                    stringProvider = stringProvider,
                )
            } else {
                mock
            }
    }
}

