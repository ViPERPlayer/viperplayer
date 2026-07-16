package com.viperplayer.follows.di

import android.content.Context
import androidx.room.Room
import com.viperplayer.follows.data.FollowedArtistDao
import com.viperplayer.follows.data.FollowedArtistsDatabase
import com.viperplayer.follows.data.FollowedArtistsRepository
import com.viperplayer.follows.data.FollowedArtistsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the self-contained follows (subscriptions) feature: its own Room database + DAO,
 * and the repository binding. Kept in the follows package so nothing in the shared DI modules needs
 * to change.
 */
@Module
@InstallIn(SingletonComponent::class)
object FollowsModule {

    @Provides
    @Singleton
    fun provideFollowedArtistsDatabase(@ApplicationContext context: Context): FollowedArtistsDatabase =
        Room.databaseBuilder(context, FollowedArtistsDatabase::class.java, "viperplayer_follows")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFollowedArtistDao(database: FollowedArtistsDatabase): FollowedArtistDao =
        database.followedArtistDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class FollowsBindingsModule {

    // Scope lives on FollowedArtistsRepositoryImpl (@Singleton); no need to repeat it on the binding.
    @Binds
    abstract fun bindFollowedArtistsRepository(
        impl: FollowedArtistsRepositoryImpl,
    ): FollowedArtistsRepository
}
