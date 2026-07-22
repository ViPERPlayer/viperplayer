package com.viperplayer.di

import com.viperplayer.data.resources.AndroidStringProvider
import com.viperplayer.data.resources.StringProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the [StringProvider] used to resolve string resources outside `@Composable` scopes. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResourcesModule {
    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
}
