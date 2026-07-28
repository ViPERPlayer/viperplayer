package com.viperplayer.data.rec

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.viperplayer.di.RecModelPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Background worker that installs the on-device CLAP audio-tower model. Enqueued (Wi-Fi-only) by
 * [ClapModelRepository] when the user opts into smart recommendations or a version handshake demands
 * a (re)download.
 *
 * Steps: fetch the manifest → run the version handshake ([ClapModelHandshake.shouldDownload]) →
 * stream the ONNX to `filesDir/rec-models/clap_audio_<version>.onnx.part` (resuming from any existing
 * `.part`) → verify the sha256 against the manifest → atomically rename into place → record the
 * installed version. Progress is published via [setProgress]; a failure carries a stable
 * [FailureReason] in the output so the Settings toggle can show the right message + retry.
 *
 * A [HiltWorker]: it takes [ClapModelApi] via the app's [androidx.hilt.work.HiltWorkerFactory].
 */
@HiltWorker
class ClapModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: ClapModelApi,
    @RecModelPreferences private val dataStore: DataStore<Preferences>,
) : CoroutineWorker(appContext, params) {

    private val modelDir: File
        get() = File(applicationContext.filesDir, MODEL_SUBDIR).apply { mkdirs() }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1. Manifest.
        val entry = when (val result = api.fetchManifest()) {
            is ClapModelApi.ManifestResult.Success -> result.entry
            ClapModelApi.ManifestResult.NotConfigured ->
                return@withContext fail(FailureReason.BACKEND_NOT_CONFIGURED)
            ClapModelApi.ManifestResult.Unavailable ->
                return@withContext retryOrFail(FailureReason.MANIFEST_UNAVAILABLE)
        }

        // 2. Version handshake. If the app can't consume this manifest version, there's nothing to
        //    download for this build — fail cleanly (the repo surfaces VersionMismatch from the
        //    installed side; here we just stop).
        val installed = installedVersion()
        if (!ClapModelHandshake.shouldDownload(installed, entry.version)) {
            // Already have a compatible model (or the manifest is for a version this app can't use).
            return@withContext if (ClapModelHandshake.isManifestCompatible(entry.version) &&
                resolveInstalledFile(entry.version) != null
            ) {
                Result.success()
            } else {
                // App is outdated relative to the manifest — not retryable by re-running.
                fail(FailureReason.MANIFEST_UNAVAILABLE)
            }
        }

        val partFile = File(modelDir, ClapModelHandshake.partFileName(entry.version))
        val finalFile = File(modelDir, ClapModelHandshake.installedFileName(entry.version))

        // 3. Stream (resuming from an existing .part if present and not larger than expected).
        val resumeFrom = partFile.takeIf { it.isFile }?.length()
            ?.takeIf { it in 1 until entry.bytes } ?: 0L
        if (resumeFrom == 0L) partFile.delete()

        setProgress(progressData(0f))

        val downloadResult = try {
            api.openDownload(entry, resumeFromByte = resumeFrom) { channel, contentLength ->
                streamToFile(channel, partFile, resumeFrom, entry.bytes, contentLength)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "CLAP model download stream failed")
            ClapModelApi.DownloadResult.Error
        }

        when (downloadResult) {
            ClapModelApi.DownloadResult.Success -> Unit
            ClapModelApi.DownloadResult.NotConfigured ->
                return@withContext fail(FailureReason.BACKEND_NOT_CONFIGURED)
            ClapModelApi.DownloadResult.Error ->
                return@withContext retryOrFail(FailureReason.NETWORK_OR_IO)
        }

        // 4. Size + sha256 verification.
        if (partFile.length() != entry.bytes) {
            Timber.w("CLAP model size mismatch: got ${partFile.length()}, expected ${entry.bytes}")
            partFile.delete()
            return@withContext retryOrFail(FailureReason.NETWORK_OR_IO)
        }
        if (!verifyFileSha256(partFile, entry.sha256)) {
            Timber.w("CLAP model checksum mismatch; discarding")
            partFile.delete()
            return@withContext fail(FailureReason.CHECKSUM_MISMATCH)
        }

        // 5. Atomic install: rename .part -> final, then record the version.
        finalFile.delete()
        if (!partFile.renameTo(finalFile)) {
            // Fallback copy if rename across the same dir somehow fails.
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        recordInstalledVersion(entry.version)
        // Prune any stale, differently-versioned installed models to reclaim space.
        pruneOtherModels(keep = finalFile)

        setProgress(progressData(1f))
        Result.success()
    }

    /** Streams [channel] into [partFile] (appending when [startOffset] > 0), publishing progress. */
    private suspend fun streamToFile(
        channel: ByteReadChannel,
        partFile: File,
        startOffset: Long,
        totalBytes: Long,
        contentLength: Long?,
    ) {
        val buffer = ByteArray(DOWNLOAD_CHUNK)
        var written = startOffset
        // For an open-ended resume the response Content-Length is the tail; total is the manifest size.
        val effectiveTotal = if (totalBytes > 0) totalBytes else contentLength?.let { startOffset + it }
        if (startOffset == 0L) partFile.delete()
        FileOutputStream(partFile, /* append = */ startOffset > 0L).use { output ->
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) continue
                output.write(buffer, 0, read)
                written += read
                val fraction = effectiveTotal?.takeIf { it > 0 }
                    ?.let { (written.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
                if (fraction != null) setProgress(progressData(fraction))
            }
            output.flush()
        }
    }

    private suspend fun installedVersion(): String? =
        runCatching { dataStore.data.first()[INSTALLED_VERSION_KEY] }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private suspend fun recordInstalledVersion(version: String) {
        dataStore.edit { it[INSTALLED_VERSION_KEY] = version }
    }

    private fun resolveInstalledFile(version: String): File? =
        File(modelDir, ClapModelHandshake.installedFileName(version)).takeIf { it.isFile }

    private fun pruneOtherModels(keep: File) {
        modelDir.listFiles()?.forEach { f ->
            if (f != keep && f.name != keep.name) f.delete()
        }
    }

    /** Streams [file] through SHA-256 and compares to the manifest's lowercase-hex digest. */
    private fun verifyFileSha256(file: File, expectedHex: String): Boolean {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_CHUNK)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedHex.trim(), ignoreCase = true)
    }

    /** Retry when WorkManager still has attempts left; otherwise fail with [reason]. */
    private fun retryOrFail(reason: FailureReason): Result =
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else fail(reason)

    private fun fail(reason: FailureReason): Result =
        Result.failure(workDataOf(ClapModelRepositoryImpl.KEY_FAILURE_REASON to reason.name))

    private fun progressData(fraction: Float): Data =
        workDataOf(ClapModelRepositoryImpl.KEY_PROGRESS to fraction)

    companion object {
        private const val MODEL_SUBDIR = "rec-models"
        private const val DOWNLOAD_CHUNK = 64 * 1024
        private const val MAX_ATTEMPTS = 4

        private val INSTALLED_VERSION_KEY = ClapModelRepositoryImpl.INSTALLED_VERSION_KEY
    }
}
