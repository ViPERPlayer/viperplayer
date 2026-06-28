package com.viperplayer.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.data.local.ViperPlayerDatabase
import com.viperplayer.data.local.migration.MIGRATION_1_2
import com.viperplayer.data.local.migration.MIGRATION_2_3
import com.viperplayer.data.local.migration.MIGRATION_3_4
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(true) // last-resort for any future unmigrated bump
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
}

