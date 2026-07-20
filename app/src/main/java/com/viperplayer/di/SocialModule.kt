package com.viperplayer.di

import com.viperplayer.data.social.FollowRepositoryImpl
import com.viperplayer.data.social.StubFriendActivityRepository
import com.viperplayer.data.social.StubFriendsRepository
import com.viperplayer.data.social.StubSharedPlaylistsRepository
import com.viperplayer.domain.social.FollowRepository
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.FriendsRepository
import com.viperplayer.domain.social.SharedPlaylistsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the (backend-gated) social feature. The friends rail / friend-activity / shared-with-you
 * surfaces are still no-backend stubs; the follow graph + discovery (social S1) is bound to its real
 * backend-backed [FollowRepositoryImpl], which self-gates to empty/NotConfigured when the backend is off.
 * Kept in its own module (not [DataModule]) so the social scaffolding stays merge-disjoint from unrelated
 * data wiring.
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

    @Binds
    abstract fun bindFollowRepository(impl: FollowRepositoryImpl): FollowRepository
}
