package com.viperplayer.data.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.viperplayer.data.player.cast.CastSessionCommands
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
@UnstableApi
@Singleton
class MediaControllerManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isCasting = MutableStateFlow(false)

    /**
     * Whether playback is currently going to a Google Cast device. Driven by the custom casting-state
     * command the [PlaybackService] broadcasts on cast connect/disconnect (received in the
     * [MediaController.Listener.onCustomCommand] below). While true the local DSP chain (ViPER FX) is
     * bypassed, so the UI surfaces the "ViPER FX unavailable while casting" notice.
     */
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

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

                            override fun onCustomCommand(
                                controller: MediaController,
                                command: SessionCommand,
                                args: Bundle
                            ): ListenableFuture<SessionResult> {
                                if (command.customAction == CastSessionCommands.ACTION_CASTING_CHANGED) {
                                    _isCasting.value =
                                        args.getBoolean(CastSessionCommands.EXTRA_IS_CASTING, false)
                                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                                }
                                return Futures.immediateFuture(
                                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                                )
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

