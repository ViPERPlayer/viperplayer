package com.viperplayer.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.data.lastfm.LastfmRepositoryImpl
import com.viperplayer.data.lastfm.PendingScrobbleDao
import com.viperplayer.data.lastfm.ScrobbleQueueDatabase
import com.viperplayer.domain.lastfm.LastfmRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the built-in Last.fm scrobbling feature. Provides the dedicated offline-queue
 * database ("viperplayer_lastfm") — kept separate from the shared library DB so this feature owns its
 * own schema, matching the stats / follows / alarm features. The [LastfmScrobbler] and
 * [com.viperplayer.data.lastfm.LastfmCredentialStore] / [com.viperplayer.data.lastfm.LastfmApi] are
 * constructor-injected singletons and need no explicit provider.
 */
@Module
@InstallIn(SingletonComponent::class)
object LastfmModule {

    @Provides
    @Singleton
    fun provideScrobbleQueueDatabase(
        @ApplicationContext context: Context,
    ): ScrobbleQueueDatabase = Room.databaseBuilder(
        context,
        ScrobbleQueueDatabase::class.java,
        "viperplayer_lastfm",
    )
        // Pre-production: no migrations maintained (matches the rest of the app). A schema change bumps
        // the version and wipes the on-device queue. Add real migrations before shipping.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providePendingScrobbleDao(database: ScrobbleQueueDatabase): PendingScrobbleDao =
        database.pendingScrobbleDao()
}

/** Binds the Last.fm repository interface to its implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class LastfmBindingsModule {

    @Binds
    @Singleton
    abstract fun bindLastfmRepository(impl: LastfmRepositoryImpl): LastfmRepository
}
