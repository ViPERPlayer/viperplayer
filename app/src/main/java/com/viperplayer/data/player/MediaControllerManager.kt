package com.viperplayer.data.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

            val listener = object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    Timber.d("onDisconnected() called with: controller = $controller")
                    close() // recreate only on crash
                }
            }

            val controller =
                MediaController.Builder(context, token)
                    .setListener(listener)
                    .buildAsync()
                    .await()

            Timber.d("Connected to MediaController: $controller")

            send(controller)

            awaitClose {
                controller.release()
            }
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            null
        ).filterNotNull()
}

