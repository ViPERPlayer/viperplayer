package com.viperplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * Host-side networking. Provides a single shared Ktor [HttpClient] for the app's own network calls
 * (e.g. the LRCLIB lyrics fallback, plugin update downloads) — plugins own their HTTP stacks
 * separately.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        // Installed with no global defaults — it only enables per-request timeout config (e.g. the
        // plugin update APK download needs a longer window than OkHttp's 10s read default).
        install(HttpTimeout)
    }
}
