package com.viperplayer.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.data.local.ViperPlayerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ViperPlayerDatabase {
        return Room.databaseBuilder(
            context,
            ViperPlayerDatabase::class.java,
            "viperplayer_database"
        )
            // Pre-production: no migrations are maintained. Any schema change bumps the version and
            // wipes the on-device DB instead of migrating. Add real migrations before shipping.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideArtistDao(database: ViperPlayerDatabase) = database.artistDao()

    @Provides
    fun provideAlbumDao(database: ViperPlayerDatabase) = database.albumDao()

    @Provides
    fun provideSongDao(database: ViperPlayerDatabase) = database.songDao()

    @Provides
    fun providePlaylistDao(database: ViperPlayerDatabase) = database.playlistDao()

    @Provides
    fun provideGenreDao(database: ViperPlayerDatabase) = database.genreDao()

    @Provides
    fun provideCrossRefDao(database: ViperPlayerDatabase) = database.crossRefDao()

    @Provides
    fun provideSearchHistoryDao(database: ViperPlayerDatabase) = database.searchHistoryDao()

    @Provides
    fun providePlayEventDao(database: ViperPlayerDatabase) = database.playEventDao()

    @Provides
    fun provideViperPresetDao(database: ViperPlayerDatabase) = database.viperPresetDao()

    @Provides
    fun provideOutboxDao(database: ViperPlayerDatabase) = database.outboxDao()
}

