package com.viperplayer.di

import com.viperplayer.data.social.StubFriendActivityRepository
import com.viperplayer.data.social.StubFriendsRepository
import com.viperplayer.data.social.StubSharedPlaylistsRepository
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.FriendsRepository
import com.viperplayer.domain.social.SharedPlaylistsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the (backend-gated) social feature. Binds the no-backend stub implementations today;
 * when the real backend-backed implementations land they replace the stubs here. Kept in its own module
 * (not [DataModule]) so the social scaffolding stays merge-disjoint from unrelated data wiring.
 *
 * [com.viperplayer.domain.social.SocialFeatures] is `@Inject`-constructable, so it needs no binding here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SocialModule {

    // Scope lives on each Stub*Repository (@Singleton); no need to repeat it on the binding.
    @Binds
    abstract fun bindFriendsRepository(impl: StubFriendsRepository): FriendsRepository

    @Binds
    abstract fun bindFriendActivityRepository(impl: StubFriendActivityRepository): FriendActivityRepository

    @Binds
    abstract fun bindSharedPlaylistsRepository(impl: StubSharedPlaylistsRepository): SharedPlaylistsRepository
}
