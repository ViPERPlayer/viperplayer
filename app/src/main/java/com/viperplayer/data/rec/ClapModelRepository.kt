package com.viperplayer.data.rec

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.viperplayer.di.RecModelPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the on-device CLAP audio-tower model asset: its installed version (persisted), its resolved
 * local file, and the WorkManager-backed download. The single source of truth the Settings toggle
 * and the (later) P1c indexer observe via [modelState].
 *
 * Version handshake: [modelState] cross-checks the installed version against both the backend
 * manifest and the app's [ClapModel.MODEL_VERSION] expectation (see [ClapModelHandshake]); a stale
 * installed version surfaces as [ClapModelState.VersionMismatch] and, when the app itself is
 * outdated, drives a (re)download rather than using an incompatible file.
 *
 * All Android/IO/WorkManager glue lives here (not in the ViewModel), per the app's MVVM rule.
 */
interface ClapModelRepository {

    /** The observable model lifecycle: Absent / Downloading / Ready / Failed / VersionMismatch. */
    val modelState: Flow<ClapModelState>

    /** The installed model version, or null when nothing is installed. */
    suspend fun installedVersion(): String?

    /** The resolved local model file if a verified model is installed, else null. */
    suspend fun installedFile(): File?

    /**
     * `true` iff an installed model's version is stale relative to [ClapModel.MODEL_VERSION] — a
     * signal that stored embeddings must be recomputed (the P1c indexer re-embeds). Pure of network.
     */
    suspend fun isInstalledStale(): Boolean

    /** Enqueues the (Wi-Fi-constrained) download worker, replacing any pending run. Idempotent. */
    fun enqueueDownload()

    /**
     * Ensures a current model is present: enqueues a download unless a compatible verified model of
     * [ClapModel.MODEL_VERSION] is already installed. Returns immediately; progress is observed via
     * [modelState].
     */
    suspend fun ensureDownloaded()

    /** Cancels any in-flight/pending download. Does not delete an already-installed model. */
    fun cancel()

    /** Deletes the installed model + version record and cancels any download (toggle turned off). */
    suspend fun deleteModel()
}

@Singleton
class ClapModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @RecModelPreferences private val dataStore: DataStore<Preferences>,
) : ClapModelRepository {

    private val workManager get() = WorkManager.getInstance(context)

    /** Directory under filesDir where installed models + `.part` files live. */
    private val modelDir: File
        get() = File(context.filesDir, MODEL_SUBDIR).apply { mkdirs() }

    private val installedVersionFlow: Flow<String?> =
        dataStore.data.map { it[INSTALLED_VERSION_KEY]?.takeIf(String::isNotBlank) }
            .distinctUntilChanged()

    override val modelState: Flow<ClapModelState> =
        combine(
            installedVersionFlow,
            workManager.getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME),
        ) { installed, workInfos ->
            deriveState(installed, workInfos.firstOrNull())
        }.distinctUntilChanged()

    override suspend fun installedVersion(): String? = installedVersionFlow.first()

    override suspend fun installedFile(): File? {
        val version = installedVersion() ?: return null
        return resolveInstalledFile(version)
    }

    override suspend fun isInstalledStale(): Boolean =
        ClapModelHandshake.isInstalledStale(installedVersion())

    override fun enqueueDownload() {
        val request = OneTimeWorkRequestBuilder<ClapModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    // Unmetered (Wi-Fi) only — the model is large; never spend the user's cellular data.
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(Data.EMPTY)
            .build()
        // KEEP: a download already running/queued is not restarted on a repeated toggle/enable.
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun ensureDownloaded() {
        val installed = installedVersion()
        // A compatible, verified, present model needs no work.
        if (installed != null &&
            !ClapModelHandshake.isInstalledStale(installed) &&
            resolveInstalledFile(installed) != null
        ) {
            return
        }
        // Prefer the model BUNDLED with the app (instant, offline, no backend/Wi-Fi) over a download.
        // When the ~73MB CLAP asset is present in the APK it is copied into place and stamped Ready.
        if (installFromBundledAssetIfPresent()) return
        enqueueDownload()
    }

    /**
     * Installs the CLAP model from the app's bundled asset ([ASSET_MODEL_PATH]) if it is present in the
     * APK: copies it into [modelDir] (atomically via a `.part` rename) and stamps the installed version
     * so [modelState] becomes Ready — no network, no Wi-Fi wait, no backend. Returns false (a no-op) when
     * the asset isn't bundled, so callers fall back to the download path. Never throws.
     */
    private suspend fun installFromBundledAssetIfPresent(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val version = ClapModel.MODEL_VERSION
            val target = File(modelDir, ClapModelHandshake.installedFileName(version))
            if (!target.isFile || target.length() == 0L) {
                val part = File(modelDir, target.name + ".part")
                context.assets.open(ASSET_MODEL_PATH).use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    return@runCatching false
                }
            }
            dataStore.edit { it[INSTALLED_VERSION_KEY] = version }
            Timber.i("CLAP model installed from bundled asset (%s)", version)
            true
        }.getOrElse {
            // Most commonly the asset simply isn't bundled (FileNotFoundException) → fall back to download.
            Timber.d("CLAP bundled-asset install unavailable: %s", it.javaClass.simpleName)
            false
        }
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    override suspend fun deleteModel() {
        cancel()
        modelDir.listFiles()?.forEach { it.delete() }
        dataStore.edit { it.remove(INSTALLED_VERSION_KEY) }
    }

    /** The installed file for [version] if it exists on disk, else null. */
    private fun resolveInstalledFile(version: String): File? =
        File(modelDir, ClapModelHandshake.installedFileName(version)).takeIf { it.isFile }

    /**
     * Folds the installed version + the download WorkInfo into a [ClapModelState] via the pure
     * [ClapModelStateMachine]. This method only adapts the `WorkInfo` into a framework-free snapshot
     * and resolves the on-disk file; the decision logic (and its tests) live in the state machine.
     */
    private fun deriveState(installed: String?, work: WorkInfo?): ClapModelState =
        ClapModelStateMachine.derive(
            installedVersion = installed,
            installedFile = installed?.let { resolveInstalledFile(it) },
            work = work?.toSnapshot(),
        )

    /** Adapts a WorkManager [WorkInfo] into the framework-free [ModelWorkSnapshot]. */
    private fun WorkInfo.toSnapshot(): ModelWorkSnapshot = when (state) {
        WorkInfo.State.RUNNING -> ModelWorkSnapshot(
            phase = WorkPhase.RUNNING,
            progress = progress.getFloat(KEY_PROGRESS, -1f).takeIf { it >= 0f },
        )
        WorkInfo.State.ENQUEUED -> ModelWorkSnapshot(
            phase = WorkPhase.ENQUEUED,
            progress = progress.getFloat(KEY_PROGRESS, -1f).takeIf { it >= 0f },
        )
        WorkInfo.State.FAILED -> ModelWorkSnapshot(
            phase = WorkPhase.FAILED,
            failureReason = outputData.getString(KEY_FAILURE_REASON)
                ?.let { runCatching { FailureReason.valueOf(it) }.getOrNull() },
        )
        else -> ModelWorkSnapshot(phase = WorkPhase.IDLE)
    }

    companion object {
        private const val MODEL_SUBDIR = "rec-models"

        /** Path of the optional model bundled in the APK's assets (kept uncompressed via noCompress). */
        private const val ASSET_MODEL_PATH = "rec-model/clap_audio_tower.int8.onnx"

        /** Unique WorkManager name so at most one download runs; a repeat enqueue is coalesced. */
        const val UNIQUE_WORK_NAME = "clap_model_download"

        /** DataStore key holding the installed model version. */
        val INSTALLED_VERSION_KEY = stringPreferencesKey("clap_model_installed_version")

        /** Worker progress key (0f..1f) in [WorkInfo.progress]. */
        const val KEY_PROGRESS = "progress"

        /** Worker failure-reason key ([FailureReason] name) in the failed [WorkInfo.outputData]. */
        const val KEY_FAILURE_REASON = "failure_reason"

        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
