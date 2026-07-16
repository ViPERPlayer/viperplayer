package com.viperplayer.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.data.stats.StatsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the dedicated listening-stats database ("viperplayer_stats"). Kept separate from
 * [DatabaseModule] so this feature owns its own DB/schema and never touches the shared library DB.
 */
@Module
@InstallIn(SingletonComponent::class)
object StatsModule {

    @Provides
    @Singleton
    fun provideStatsDatabase(
        @ApplicationContext context: Context,
    ): StatsDatabase = Room.databaseBuilder(
        context,
        StatsDatabase::class.java,
        "viperplayer_stats",
    )
        // Pre-production: no migrations maintained (matches the rest of the app). A schema change
        // bumps the version and wipes the on-device stats DB. Add real migrations before shipping.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun providePlayHistoryDao(database: StatsDatabase): PlayHistoryDao = database.playHistoryDao()
}
