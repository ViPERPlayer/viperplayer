package com.viperplayer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.data.rec.ClapModelRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt wiring for the on-device recommender (P1b: the opt-in CLAP model download). Provides the
 * single shared Preferences DataStore for model bookkeeping (installed version), qualified so it is
 * never confused with the app-wide "settings" store, and binds the [ClapModelRepository].
 *
 * The DataStore is provided (not created via a `Context` extension delegate) so exactly one instance
 * backs both the repository and the download worker — two `preferencesDataStore` delegates on the
 * same file name would crash with "multiple DataStores active for the same file".
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecModule {

    @Binds
    @Singleton
    abstract fun bindClapModelRepository(impl: ClapModelRepositoryImpl): ClapModelRepository

    companion object {
        private val Context.recModelDataStore: DataStore<Preferences> by preferencesDataStore(name = "rec_model")

        @Provides
        @Singleton
        @RecModelPreferences
        fun provideRecModelDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.recModelDataStore
    }
}

/** Qualifies the recommender-model Preferences DataStore (distinct from the app "settings" store). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecModelPreferences
