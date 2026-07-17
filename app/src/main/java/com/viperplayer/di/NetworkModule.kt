package com.viperplayer.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import javax.inject.Singleton

/**
 * Host-side networking. Provides a single shared Ktor [HttpClient] for the app's own network calls
 * (e.g. the LRCLIB lyrics fallback, plugin update downloads, the Listen-Together session socket) —
 * plugins own their HTTP stacks separately.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        // Per-request timeout config (e.g. the plugin update APK download needs a longer window than
        // OkHttp's 10s read default). No global defaults are set, so existing plain-HTTP callers are
        // unaffected.
        install(HttpTimeout)
        // Enables ws(s):// upgrades for the Jam session socket (JamSocketClient). The OkHttp engine
        // carries the WS transport; this only opts the plugin in — HTTP requests are untouched.
        install(WebSockets)
    }
}
