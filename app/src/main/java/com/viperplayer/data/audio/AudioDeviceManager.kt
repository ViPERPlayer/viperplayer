package com.viperplayer.data.audio

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.RouteInfo
import com.viperplayer.domain.audio.AudioOutputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio output device detection and provides reactive updates.
 * Uses MediaRouter for route change detection (casting, manual device selection, etc.).
 * AudioManager is only used to get device details (product name, address, type) for local devices,
 * as MediaRouter's system route doesn't provide this information.
 */
@Singleton
class AudioDeviceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mediaRouter: MediaRouter = MediaRouter.getInstance(context)

    private val _currentDevice = MutableStateFlow<AudioOutputDevice?>(null)
    
    /**
     * Flow of the current audio output device.
     * Emits null if no device is detected or if using default routing.
     */
    val currentDevice: StateFlow<AudioOutputDevice?> = _currentDevice.asStateFlow()

    // MediaRouter callback for all route changes (casting, manual selection, system route changes)
    private val mediaRouterCallback: MediaRouter.Callback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: RouteInfo, reason: Int) {
            Timber.d("Route selected: ${route.name}")
            updateCurrentDeviceFromRoute()
        }

        override fun onRouteUnselected(router: MediaRouter, route: RouteInfo, reason: Int) {
            Timber.d("Route unselected: ${route.name}")
            updateCurrentDeviceFromRoute()
        }

        override fun onRouteChanged(router: MediaRouter, route: RouteInfo) {
            Timber.d("Route changed: ${route.name}")
            updateCurrentDeviceFromRoute()
        }

        override fun onRouteAdded(router: MediaRouter, route: RouteInfo) {
            Timber.d("Route added: ${route.name}")
            updateCurrentDeviceFromRoute()
        }

        override fun onRouteRemoved(router: MediaRouter, route: RouteInfo) {
            Timber.d("Route removed: ${route.name}")
            updateCurrentDeviceFromRoute()
        }
    }

    init {
        // Register MediaRouter callback for all route changes
        mediaRouter.addCallback(MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_AUDIO_PLAYBACK)
            .build(), mediaRouterCallback)

        // Get initial device
        updateCurrentDeviceFromRoute()
    }

    /**
     * Updates the current device from MediaRouter's selected route.
     * Handles both remote routes (casting) and system routes (local playback).
     * For system routes, uses AudioManager to get device details since MediaRouter
     * only tells us it's a system route, not which specific device.
     */
    private fun updateCurrentDeviceFromRoute() {
        val selectedRoute = mediaRouter.selectedRoute
        val newDevice = createDeviceInfoFromRoute(selectedRoute)
        Timber.d("Selected route: ${selectedRoute.name}, device: $newDevice")
        _currentDevice.value = newDevice
    }

    /**
     * Creates an AudioOutputDevice from MediaRouter RouteInfo (for casting/remote routes).
     */
    private fun createDeviceInfoFromRoute(route: RouteInfo): AudioOutputDevice {
        return AudioOutputDevice(
            id = route.id,
            name = route.name,
        )
    }
}

