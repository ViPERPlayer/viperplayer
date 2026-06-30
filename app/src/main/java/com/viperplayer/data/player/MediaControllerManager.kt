package com.viperplayer.data.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.guava.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the MediaController connection to PlaybackService.
 * This is the bridge between the repository and the ExoPlayer service.
 */
@Singleton
class MediaControllerManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val controllerFlow: Flow<MediaController> =
        callbackFlow {
            Timber.d("Connecting to MediaController...")

            val token = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )

            var current: MediaController? = null

            // Rebuild on disconnect (e.g. a service crash) instead of close()ing — a completed
            // callbackFlow + stateIn(Eagerly) would cache the RELEASED controller forever, so every
            // transport command would silently hit a dead controller until the app was restarted.
            fun connect() {
                launch {
                    try {
                        val listener = object : MediaController.Listener {
                            override fun onDisconnected(controller: MediaController) {
                                Timber.d("MediaController disconnected — reconnecting")
                                controller.release()
                                connect()
                            }
                        }
                        val controller = MediaController.Builder(context, token)
                            .setListener(listener)
                            .buildAsync()
                            .await()
                        current = controller
                        Timber.d("Connected to MediaController: $controller")
                        trySend(controller)
                    } catch (e: Exception) {
                        Timber.w(e, "MediaController connect failed; retrying in 1s")
                        delay(1000)
                        connect()
                    }
                }
            }

            connect()

            awaitClose {
                current?.release()
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            null
        ).filterNotNull()
}

