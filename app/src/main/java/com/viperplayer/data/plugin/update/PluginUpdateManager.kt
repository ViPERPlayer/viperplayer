package com.viperplayer.data.plugin.update

import android.content.Context
import android.content.pm.PackageManager
import com.viperplayer.data.preferences.PluginUpdatePreferences
import com.viperplayer.plugin.host.PluginDiscovery
import com.viperplayer.plugin.protocol.Protocol
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Host-side plugin update framework. For each INSTALLED plugin it:
 *  1. reads the plugin's current versionCode + optional update-feed URL (a `<meta-data>` in the
 *     plugin manifest — see [Protocol.META_UPDATE_URL]) via [PackageManager];
 *  2. fetches the plugin's [PluginUpdateManifest] off-main;
 *  3. compares versions with the pure [PluginUpdateLogic] (honouring minHostVersion + dismissal);
 *  4. surfaces the result as [availableUpdates] for the UI, and can download + install a chosen
 *     update via [PluginUpdateInstaller] (no root; the user confirms in the system UI).
 *
 * A plugin without an update URL simply has no feed and is skipped — updates are strictly opt-in
 * per plugin. All version-source details are documented on [Protocol.META_UPDATE_URL] and
 * [PluginUpdateManifest].
 *
 * The pure decision logic lives in [PluginUpdateLogic]; this class owns only the Android surface
 * (PackageManager, networking, files, PackageInstaller, persistence).
 */
@Singleton
class PluginUpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val httpClient: HttpClient,
    private val preferences: PluginUpdatePreferences,
    private val installer: PluginUpdateInstaller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _availableUpdates = MutableStateFlow<Map<String, PluginUpdate>>(emptyMap())
    /** Updates currently on offer, keyed by plugin id. Empty until the first successful check. */
    val availableUpdates: StateFlow<Map<String, PluginUpdate>> = _availableUpdates.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    /** True while a check-for-updates pass is running (for a spinner / disabled button). */
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _progress = MutableSharedFlow<PluginUpdateProgress>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    /** Live download/install progress for the UI to render a bar and success/error toasts. */
    val progress: SharedFlow<PluginUpdateProgress> = _progress.asSharedFlow()

    private val checkMutex = Mutex()
    // Guards against launching two installs for the same plugin at once.
    private val installing = mutableSetOf<String>()
    private val installMutex = Mutex()

    /**
     * Throttled entry point for the automatic app-start check. Runs a real check only when the
     * cadence ([PluginUpdateLogic.shouldCheck]) says one is due; otherwise no-ops. Never throws.
     */
    fun checkOnStartThrottled() {
        scope.launch {
            val now = System.currentTimeMillis()
            if (!PluginUpdateLogic.shouldCheck(preferences.lastCheckMs(), now)) {
                Timber.d("Plugin update check not due yet; skipping")
                return@launch
            }
            runCatching { checkNow() }.onFailure { Timber.w(it, "Startup update check failed") }
        }
    }

    /**
     * Force a check now (the manual "Check for updates" action). Refreshes [availableUpdates] and
     * records the check time. Safe to call concurrently — a second call while one is running waits.
     */
    suspend fun checkNow(): Map<String, PluginUpdate> = checkMutex.withLock {
        _isChecking.value = true
        try {
            // Discovery + PackageManager queries + network are blocking/IPC — keep them off-main so
            // a manual check from a Main-dispatched ViewModel scope never stalls the UI thread.
            withContext(Dispatchers.IO) { checkInternal() }
        } finally {
            _isChecking.value = false
        }
    }

    private suspend fun checkInternal(): Map<String, PluginUpdate> {
        val hostVersion = hostVersionCode()
        val dismissed = preferences.allDismissedVersions()
        val updates = mutableMapOf<String, PluginUpdate>()

        for (plugin in PluginDiscovery.discover(context)) {
            val pluginId = plugin.id
            val updateUrl = updateUrlFor(pluginId) ?: continue // no feed → skip gracefully
            // Defense-in-depth: refuse a non-https feed URL in code before we ever fetch it, so a
            // cleartext feed is rejected regardless of platform network-security settings.
            if (!PluginUpdateLogic.isHttpsUrl(updateUrl)) {
                Timber.w("Plugin %s update feed URL is not https; skipping", pluginId)
                continue
            }
            val installedCode = installedVersionCode(pluginId) ?: continue

            val manifest = fetchManifest(updateUrl) ?: continue
            // Refuse a non-https download URL in code too — never hand a cleartext APK URL to the
            // downloader even if the platform would otherwise permit it.
            if (!PluginUpdateLogic.isHttpsUrl(manifest.downloadUrl)) {
                Timber.w("Plugin %s update download URL is not https; treating as no update", pluginId)
                continue
            }
            // Missing integrity hash is allowed (package + signature enforcement still protects the
            // install), but make it visible so a feed silently dropping its sha256 is noticeable.
            if (manifest.sha256.isNullOrBlank()) {
                Timber.w(
                    "Plugin %s update manifest has no sha256; relying on package+signature enforcement",
                    pluginId,
                )
            }
            val evaluation = PluginUpdateLogic.evaluate(
                installedVersionCode = installedCode,
                hostVersionCode = hostVersion,
                manifest = manifest,
                dismissedVersionCode = dismissed[pluginId],
            )
            if (evaluation is UpdateEvaluation.Available) {
                updates[pluginId] = PluginUpdate(
                    pluginId = pluginId,
                    installedVersionName = plugin.version,
                    availableVersionCode = manifest.versionCode,
                    availableVersionName = manifest.versionName,
                    downloadUrl = manifest.downloadUrl,
                    changelog = manifest.changelog,
                    sha256 = manifest.sha256,
                )
            }
        }

        _availableUpdates.value = updates
        preferences.setLastCheckMs(System.currentTimeMillis())
        Timber.d("Plugin update check complete: ${updates.size} update(s) available")
        return updates
    }

    /** Dismiss the currently-offered update for [pluginId] (kept quiet until a newer build appears). */
    suspend fun dismiss(pluginId: String) {
        val update = _availableUpdates.value[pluginId] ?: return
        preferences.setDismissedVersion(pluginId, update.availableVersionCode)
        _availableUpdates.update { it - pluginId }
    }

    /**
     * Download the update APK for [pluginId] and hand it to the system installer. Emits
     * [PluginUpdateProgress] throughout and returns when the run reaches a terminal state. A no-op if
     * there's no offered update or an install for this plugin is already in flight.
     */
    fun downloadAndInstall(pluginId: String) {
        scope.launch {
            val alreadyRunning = installMutex.withLock {
                if (pluginId in installing) true else { installing.add(pluginId); false }
            }
            if (alreadyRunning) return@launch

            try {
                val update = _availableUpdates.value[pluginId]
                if (update == null) {
                    emit(PluginUpdateProgress.Failed(pluginId, "No update to install"))
                    return@launch
                }
                runDownloadAndInstall(pluginId, update)
            } catch (e: Exception) {
                Timber.e(e, "Update download/install failed for $pluginId")
                emit(PluginUpdateProgress.Failed(pluginId, e.message ?: "Update failed"))
            } finally {
                installMutex.withLock { installing.remove(pluginId) }
            }
        }
    }

    private suspend fun runDownloadAndInstall(pluginId: String, update: PluginUpdate) {
        emit(PluginUpdateProgress.Downloading(pluginId, fraction = null))
        val apk = downloadApk(pluginId, update) ?: run {
            emit(PluginUpdateProgress.Failed(pluginId, "Download failed"))
            return
        }
        try {
            emit(PluginUpdateProgress.Installing(pluginId))
            // The installer fires this once the system actually asks the user to confirm, so the
            // "awaiting confirmation" state reflects reality (not a guess before the commit).
            val onPendingUserAction = {
                scope.launch { emit(PluginUpdateProgress.AwaitingUserConfirmation(pluginId)) }
                Unit
            }
            when (val result = installer.install(pluginId, apk, onPendingUserAction)) {
                is PluginUpdateInstaller.InstallResult.Success -> {
                    // The reinstall triggers PluginDataSource's package-replaced re-discovery; drop
                    // the now-stale offer here so the badge clears immediately.
                    _availableUpdates.update { it - pluginId }
                    emit(PluginUpdateProgress.Succeeded(pluginId))
                }
                is PluginUpdateInstaller.InstallResult.Failure ->
                    emit(PluginUpdateProgress.Failed(pluginId, result.message))
            }
        } finally {
            runCatching { apk.delete() }
        }
    }

    /**
     * Stream the APK to a private cache file, emitting download progress and (when the manifest
     * carries a [PluginUpdate.sha256]) verifying the digest. Returns the file, or null on failure /
     * checksum mismatch.
     */
    private suspend fun downloadApk(pluginId: String, update: PluginUpdate): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "plugin_updates").apply { mkdirs() }
        val outFile = File(dir, "$pluginId-${update.availableVersionCode}.apk")
        outFile.delete()

        val digest = update.sha256?.let { MessageDigest.getInstance("SHA-256") }
        var ok = false

        try {
            httpClient.prepareGet(update.downloadUrl) {
                // The shared client sets no timeouts (and OkHttp's 10s read timeout would trip a
                // large APK on a slow link) — give the download a generous per-request window.
                timeout {
                    requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS
                    socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    // An error page / redirect is not an APK — fail cleanly instead of streaming HTML
                    // into the .apk and handing garbage to PackageInstaller.
                    Timber.w("APK download for $pluginId returned HTTP ${response.status.value}")
                    return@execute
                }
                val total = response.headers["Content-Length"]?.toLongOrNull()
                val channel: ByteReadChannel = response.bodyAsChannel()
                val buffer = ByteArray(DOWNLOAD_CHUNK)
                var downloaded = 0L
                outFile.outputStream().use { output ->
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) continue
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                        downloaded += read
                        val fraction = total?.takeIf { it > 0 }?.let {
                            (downloaded.toFloat() / it.toFloat()).coerceIn(0f, 1f)
                        }
                        emit(PluginUpdateProgress.Downloading(pluginId, fraction))
                    }
                }
                ok = true
            }
        } catch (e: Exception) {
            Timber.e(e, "APK download failed for $pluginId")
            outFile.delete()
            return@withContext null
        }

        if (!ok) {
            // Non-2xx response (guarded above) — nothing usable was written.
            outFile.delete()
            return@withContext null
        }

        if (digest != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(update.sha256, ignoreCase = true)) {
                Timber.w("Checksum mismatch for $pluginId: expected ${update.sha256}, got $actual")
                outFile.delete()
                // Thrown (not returned null) so the single caller emits one specific failure — the
                // generic "Download failed" is only for a network error (the null path).
                throw ChecksumMismatchException()
            }
        }
        outFile
    }

    /** A downloaded APK's SHA-256 did not match the manifest's — treated as a hard install failure. */
    private class ChecksumMismatchException :
        Exception("Downloaded file failed integrity check")

    private suspend fun fetchManifest(url: String): PluginUpdateManifest? = try {
        val response = httpClient.get(url) {
            timeout {
                requestTimeoutMillis = MANIFEST_TIMEOUT_MS
                socketTimeoutMillis = MANIFEST_TIMEOUT_MS
            }
        }
        if (response.status.isSuccess()) {
            PluginUpdateManifest.parse(response.bodyAsText())
        } else {
            Timber.w("Manifest fetch from $url returned HTTP ${response.status.value}")
            null
        }
    } catch (e: Exception) {
        Timber.w(e, "Failed to fetch update manifest from $url")
        null
    }

    /** The plugin's advertised update-feed URL from its manifest meta-data, or null if it has none. */
    private fun updateUrlFor(pluginId: String): String? = runCatching {
        val appInfo = context.packageManager.getApplicationInfo(pluginId, PackageManager.GET_META_DATA)
        appInfo.metaData?.getString(Protocol.META_UPDATE_URL)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Current on-device versionCode of [pluginId], or null if it's not installed. */
    private fun installedVersionCode(pluginId: String): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(pluginId, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }.getOrNull()

    /** The host app's own versionCode, for minHostVersion gating. */
    private fun hostVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }.getOrElse { 0L }

    private suspend fun emit(progress: PluginUpdateProgress) {
        _progress.emit(progress)
    }

    private companion object {
        const val DOWNLOAD_CHUNK = 64 * 1024
        const val MANIFEST_TIMEOUT_MS = 15_000L
        // APKs can be large on a slow link — allow a long overall window and a generous per-read gap.
        const val DOWNLOAD_TIMEOUT_MS = 5 * 60_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MS = 60_000L
    }
}
