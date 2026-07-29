package com.viperplayer.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.viperplayer.data.rec.AudioDecoder
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.data.rec.ClapModelRepositoryImpl
import com.viperplayer.data.rec.MediaCodecAudioDecoder
import com.viperplayer.data.rec.SongAudioSource
import com.viperplayer.data.rec.SongAudioSourceResolver
import com.viperplayer.data.rec.StreamingAudioSource
import com.viperplayer.data.rec.StreamingAudioSourceResolver
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

    /** The library-embedding pipeline resolves local audio through the concrete resolver. */
    @Binds
    @Singleton
    abstract fun bindSongAudioSource(impl: SongAudioSourceResolver): SongAudioSource

    /** The streaming-embedding pipeline (Path B) resolves plugin streams through the concrete resolver. */
    @Binds
    @Singleton
    abstract fun bindStreamingAudioSource(impl: StreamingAudioSourceResolver): StreamingAudioSource

    companion object {
        private val Context.recModelDataStore: DataStore<Preferences> by preferencesDataStore(name = "rec_model")

        @Provides
        @Singleton
        @RecModelPreferences
        fun provideRecModelDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.recModelDataStore

        /**
         * The on-device audio decoder used by the library-embedding indexer. Provided (not
         * constructor-injected) because [MediaCodecAudioDecoder] has a defaulted constructor param;
         * this pins the default decode budget and keeps the interface swap in one place.
         */
        @Provides
        @Singleton
        fun provideAudioDecoder(): AudioDecoder = MediaCodecAudioDecoder()
    }
}

/** Qualifies the recommender-model Preferences DataStore (distinct from the app "settings" store). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecModelPreferences
