package com.viperplayer.data.social

import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 2, part C: the single owner of the synced-playback role switch.
 *
 * Collects [ListenTogetherRepository.currentSession] and puts this device in exactly one role at a
 * time based on [ListenSession.canControl]:
 * - **controller** ([SessionPlaybackHost]) — mirror the local player into the session;
 * - **follower** ([SessionPlaybackFollower]) — drive the local player from the shared timeline.
 * When the session ends (or the role flips), the active side is torn down and the other (if any) is
 * started. Both run on a Main-dispatched app scope so the media controller (main-thread only) is safe.
 *
 * [start] once from [com.viperplayer.ViperPlayerApplication.onCreate] so synced playback works even
 * with no screen open. Idempotent.
 */
@Singleton
class SessionPlaybackCoordinator @Inject constructor(
    private val listenTogether: ListenTogetherRepository,
    private val playerRepository: PlayerRepository,
) {
    /** Main-dispatched: the follower/host drive the main-thread-only media controller. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Test-only loop caps so a virtual-time test's advanceUntilIdle terminates (the follower/host loops
    // are otherwise infinite). null in prod. Settable only via the internal test constructor below.
    private var followerMaxTicks: Int? = null
    private var hostMaxSamples: Int? = null

    private val follower by lazy { SessionPlaybackFollower(listenTogether, playerRepository, followerMaxTicks) }
    private val host by lazy { SessionPlaybackHost(listenTogether, playerRepository, hostMaxSamples) }

    /** Test-only constructor that caps the internal loops so virtual-time tests terminate. */
    internal constructor(
        listenTogether: ListenTogetherRepository,
        playerRepository: PlayerRepository,
        followerMaxTicks: Int?,
        hostMaxSamples: Int?,
    ) : this(listenTogether, playerRepository) {
        this.followerMaxTicks = followerMaxTicks
        this.hostMaxSamples = hostMaxSamples
    }

    /** The role currently active, so the collector only switches on a real change. */
    private enum class Role { NONE, HOST, FOLLOWER }
    private var role: Role = Role.NONE

    private var started = false

    /** Begin observing the session role. Call once at app startup. Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            listenTogether.currentSession
                .map { it.toRole() }
                .distinctUntilChanged()
                .collect { applyRole(it) }
        }
    }

    private fun ListenSession?.toRole(): Role = when {
        this == null -> Role.NONE
        canControl -> Role.HOST
        else -> Role.FOLLOWER
    }

    /** Tear down the previous role's coordinator and start the new one. */
    private fun applyRole(next: Role) {
        if (next == role) return
        Timber.i("Synced playback role: $role -> $next")
        // Deactivate the current role.
        when (role) {
            Role.HOST -> host.stop()
            Role.FOLLOWER -> follower.stop(scope)
            Role.NONE -> Unit
        }
        // Activate the new role.
        when (next) {
            Role.HOST -> host.start(scope)
            Role.FOLLOWER -> follower.start(scope)
            Role.NONE -> Unit
        }
        role = next
    }

    /**
     * True while this follower can't resolve the host's current track on this device (for the UI's
     * "can't play this track" state). Reactive; false whenever not following (the follower resets it on
     * start/stop), so the ViewModel can observe it directly and gate on the follower role.
     */
    val followerTrackUnavailable: StateFlow<Boolean> get() = follower.trackUnavailable
}
