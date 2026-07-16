package com.viperplayer.data.audio

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.viperplayer.domain.audio.BluetoothCodecInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads the active Bluetooth A2DP audio codec of the currently-connected output.
 *
 * Everything here is read-only and best-effort: the codec status APIs
 * (`BluetoothA2dp.getCodecStatus` → `BluetoothCodecStatus`/`BluetoothCodecConfig`) are
 * `@SystemApi`/hidden on most Android versions, so we reach them via reflection and DEGRADE
 * GRACEFULLY — any failure (missing API, no `BLUETOOTH_CONNECT` permission, no A2DP device, OEM
 * blocking reflection) returns null rather than crashing.
 *
 * The pure codec-int → name mapping lives in [BluetoothCodecInfo] so it can be unit-tested on the JVM.
 */
@Singleton
class BluetoothCodecReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager: AudioManager? =
        ContextCompat.getSystemService(context, AudioManager::class.java)

    private val bluetoothAdapter: BluetoothAdapter? =
        ContextCompat.getSystemService(context, BluetoothManager::class.java)?.adapter

    /** True when audio is currently routed to a Bluetooth A2DP (music) output device. */
    fun isBluetoothA2dpRoute(): Boolean {
        val am = audioManager ?: return false
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.isBluetoothA2dp() }
        }.getOrDefault(false)
    }

    /** True when we still need the runtime BLUETOOTH_CONNECT permission to read the codec. */
    fun needsBluetoothConnectPermission(): Boolean = !hasBluetoothConnectPermission()

    /**
     * The active Bluetooth A2DP codec, or null when not on a BT route, the codec can't be read, or
     * permission isn't granted. All blocking binder IPC (proxy acquisition + reflective codec-status
     * reads) runs on [Dispatchers.IO], so this is safe to call from a main-thread coroutine.
     */
    suspend fun readActiveCodec(): BluetoothCodecInfo? {
        if (!isBluetoothA2dpRoute()) return null
        if (!hasBluetoothConnectPermission()) {
            Timber.d("BLUETOOTH_CONNECT not granted — cannot read BT codec")
            return null
        }
        val adapter = bluetoothAdapter ?: return null
        if (!adapter.isEnabled) return null

        return withContext(Dispatchers.IO) {
            val proxy = acquireA2dpProxy(adapter) ?: return@withContext null
            try {
                readCodecFromProxy(proxy)
            } finally {
                runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
            }
        }
    }

    /**
     * Emits the active codec now and again whenever the BT route/codec/connection changes, so the UI
     * stays in sync when the user switches codec/LDAC quality or changes route while a screen is open.
     * Emissions are debounced and each re-read runs off the main thread (see [readActiveCodec]). The
     * broadcast receiver + audio-device callback are unregistered when the flow is cancelled.
     */
    fun codecFlow(): Flow<BluetoothCodecInfo?> = changeSignals()
        .onStart { emit(Unit) }
        .debounce(REFRESH_DEBOUNCE_MS)
        .map { readActiveCodec() }

    /**
     * Bare "something relevant changed" signals from A2DP broadcasts and audio-device route changes;
     * the caller maps each to a fresh codec read. Falls back to just the initial [onStart] emission
     * if neither source can be registered.
     */
    private fun changeSignals(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ACTION_ACTIVE_DEVICE_CHANGED)
            addAction(ACTION_CODEC_CONFIG_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { Timber.d(it, "Could not register A2DP change receiver") }

        // AudioManager route changes catch switches to/from BT that the A2DP broadcasts miss.
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                trySend(Unit)
            }
        }
        runCatching { audioManager?.registerAudioDeviceCallback(audioCallback, null) }
            .onFailure { Timber.d(it, "Could not register audio-device callback") }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { audioManager?.unregisterAudioDeviceCallback(audioCallback) }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        // BLUETOOTH_CONNECT is only a runtime permission on API 31+; below that the legacy install
        // permissions cover it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Acquires the A2DP profile proxy, waiting (with a timeout) for the async service connection. */
    private suspend fun acquireA2dpProxy(adapter: BluetoothAdapter): BluetoothProfile? =
        runCatching {
            withTimeoutOrNullSafe {
                suspendCancellableCoroutine { cont ->
                    // Holds the bound proxy so cancellation (or a post-cancellation bind) can close it
                    // exactly once and never leak the profile.
                    // All access to `closed`/`boundProxy` is guarded by `closeLock` so the
                    // main-thread service callback and the cancellation thread can't both close
                    // the same proxy (or miss the write) in the bind-at-timeout race.
                    val closeLock = Any()
                    var boundProxy: BluetoothProfile? = null
                    var closed = false
                    fun closeOnce(proxy: BluetoothProfile?) {
                        synchronized(closeLock) {
                            if (proxy != null && !closed) {
                                closed = true
                                runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
                            }
                        }
                    }
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            synchronized(closeLock) { boundProxy = proxy }
                            if (cont.isActive) {
                                cont.resume(proxy)
                            } else {
                                // Bound after cancellation/timeout — close it so it doesn't leak.
                                closeOnce(proxy)
                            }
                        }

                        override fun onServiceDisconnected(profile: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    cont.invokeOnCancellation { closeOnce(synchronized(closeLock) { boundProxy }) }
                    val requested = adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)
                    if (!requested && cont.isActive) cont.resume(null)
                }
            }
        }.getOrNull()

    /**
     * Reflectively reads `BluetoothA2dp.getCodecStatus(device)` →
     * `BluetoothCodecStatus.getCodecConfig()` → `getCodecType()` / `getCodecSpecific1()` for the
     * active A2DP device. All hidden-API access is guarded; failure returns null.
     */
    @Suppress("MissingPermission") // guarded by hasBluetoothConnectPermission() before we get here
    private fun readCodecFromProxy(proxy: BluetoothProfile): BluetoothCodecInfo? = runCatching {
        val device = activeA2dpDevice(proxy) ?: return null

        val getCodecStatus = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
        val codecStatus = getCodecStatus.invoke(proxy, device) ?: return null

        val getCodecConfig = codecStatus.javaClass.getMethod("getCodecConfig")
        val codecConfig = getCodecConfig.invoke(codecStatus) ?: return null

        val codecType = codecConfig.javaClass.getMethod("getCodecType").invoke(codecConfig) as? Int
            ?: return null
        val codecSpecific1 = runCatching {
            codecConfig.javaClass.getMethod("getCodecSpecific1").invoke(codecConfig) as? Long
        }.getOrNull()

        BluetoothCodecInfo.from(codecType, codecSpecific1).also {
            Timber.d("Active BT codec: type=$codecType specific1=$codecSpecific1 -> $it")
        }
    }.onFailure { Timber.d(it, "Could not read BT codec status (hidden API unavailable)") }.getOrNull()

    /** Picks the connected A2DP device (prefers the one the framework marks active, if exposed). */
    @Suppress("MissingPermission")
    private fun activeA2dpDevice(proxy: BluetoothProfile): BluetoothDevice? = runCatching {
        val connected = proxy.connectedDevices
        if (connected.isEmpty()) return null
        // getActiveDevice() is hidden; use it when present so multi-device setups pick the right one.
        val active = runCatching {
            proxy.javaClass.getMethod("getActiveDevice").invoke(proxy) as? BluetoothDevice
        }.getOrNull()
        active?.takeIf { it in connected } ?: connected.first()
    }.getOrNull()

    private fun AudioDeviceInfo.isBluetoothA2dp(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    /** Small timeout wrapper so a stuck profile binding can't hang the caller. */
    private suspend fun <T> withTimeoutOrNullSafe(block: suspend () -> T): T? =
        withTimeoutOrNull(PROXY_TIMEOUT_MS) { block() }

    private companion object {
        const val PROXY_TIMEOUT_MS = 2_000L
        const val REFRESH_DEBOUNCE_MS = 400L

        // Hidden BluetoothA2dp actions the framework broadcasts on active-device / codec changes. Not
        // in the public SDK, so referenced by their stable string values; harmless if never fired.
        const val ACTION_ACTIVE_DEVICE_CHANGED =
            "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED"
        const val ACTION_CODEC_CONFIG_CHANGED =
            "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"
    }
}
