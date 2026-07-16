package com.viperplayer.data.plugin.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs a downloaded plugin APK via the Android [PackageInstaller] Session API — the no-root,
 * user-confirmed install path. Streams the APK bytes into a session, commits it, then waits for the
 * system's result (which routes through a runtime-registered [BroadcastReceiver]). The user still
 * confirms the install in the system UI; this class just drives the session and reports the outcome.
 */
@Singleton
class PluginUpdateInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Outcome of a commit: the system either finished, needs the user to confirm, or failed. */
    sealed interface InstallResult {
        data object Success : InstallResult
        data class Failure(val message: String) : InstallResult
    }

    /** Per-session state: the deferred to complete, plus a one-shot "user is being asked" callback. */
    private class PendingSession(
        val deferred: CompletableDeferred<InstallResult>,
        val onPendingUserAction: () -> Unit,
    )

    // Pending commits keyed by the session id we put on the callback intent.
    private val pending = ConcurrentHashMap<Int, PendingSession>()
    private val requestCodes = AtomicInteger(1)

    @Volatile
    private var receiverRegistered = false

    /**
     * Install the APK at [apkFile] for [pluginId]. Suspends until the system reports success/failure.
     * When the user must confirm, the system dialog is launched and [onPendingUserAction] fires once
     * (so the caller can show a truthful "awaiting confirmation" state). Returns a [InstallResult].
     */
    suspend fun install(
        pluginId: String,
        apkFile: File,
        onPendingUserAction: () -> Unit = {},
    ): InstallResult = withContext(Dispatchers.IO) {
        ensureReceiver()
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(pluginId)

        val sessionId = installer.createSession(params)
        val deferred = CompletableDeferred<InstallResult>()
        pending[sessionId] = PendingSession(deferred, onPendingUserAction)

        try {
            installer.openSession(sessionId).use { session ->
                apkFile.inputStream().use { input ->
                    session.openWrite("plugin-update", 0, apkFile.length()).use { output ->
                        copy(input, output)
                        session.fsync(output)
                    }
                }
                session.commit(buildStatusIntentSender(sessionId))
            }
            deferred.await()
        } catch (e: CancellationException) {
            // Don't swallow cancellation — clean up the session and rethrow.
            pending.remove(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Install session failed for $pluginId")
            pending.remove(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            InstallResult.Failure(e.message ?: "Install failed")
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
    }

    private fun buildStatusIntentSender(sessionId: Int): IntentSender {
        val intent = Intent(ACTION_INSTALL_STATUS).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        // FLAG_MUTABLE: the system fills in status extras on the callback intent (API 31+ requires
        // an explicit mutability flag).
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodes.getAndIncrement(),
            intent,
            flags,
        )
        return pendingIntent.intentSender
    }

    @Synchronized
    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_INSTALL_STATUS)
        // Not exported: only our own PendingIntent-triggered broadcasts should reach it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(statusReceiver, filter)
        }
        receiverRegistered = true
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null || intent.action != ACTION_INSTALL_STATUS) return
            val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
            val session = pending[sessionId]

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // The system needs the user to confirm — launch the confirmation activity. The
                    // terminal status arrives in a later broadcast, so don't complete the deferred.
                    val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { confirmIntent?.let { context.startActivity(it) } }
                        .onSuccess {
                            // Only now is the user genuinely being asked — report the truthful state.
                            runCatching { session?.onPendingUserAction?.invoke() }
                        }
                        .onFailure {
                            Timber.e(it, "Failed to launch install confirmation")
                            pending.remove(sessionId)
                            session?.deferred?.complete(
                                InstallResult.Failure("Couldn't show the install prompt")
                            )
                        }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    pending.remove(sessionId)
                    session?.deferred?.complete(InstallResult.Success)
                }

                Int.MIN_VALUE -> Unit // no status — ignore stray broadcast

                else -> {
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    pending.remove(sessionId)
                    session?.deferred?.complete(InstallResult.Failure(message ?: statusLabel(status)))
                }
            }
        }
    }

    private fun statusLabel(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install blocked"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicts with an installed app"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Incompatible with this device"
        PackageInstaller.STATUS_FAILURE_INVALID -> "Invalid APK"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
        else -> "Install failed"
    }

    private companion object {
        const val ACTION_INSTALL_STATUS = "com.viperplayer.plugin.update.INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "com.viperplayer.plugin.update.SESSION_ID"
    }
}
