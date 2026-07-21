package com.viperplayer.data.social

import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.ListenSession
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.PlaybackState
import com.viperplayer.domain.model.SessionParticipant
import com.viperplayer.domain.model.SessionPlayback
import com.viperplayer.domain.model.SessionTrack
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.ListenTogetherRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SessionPlaybackCoordinator] role switching over fakes. Verifies: no session → nothing
 * driven; a follower session enables follower mode + loads the shared track; a host session seeds the
 * session (controlSetTrack) without touching the local player as a follower; a role flip switches sides;
 * and leaving tears the active side down and re-enables autoplay.
 */
class SessionPlaybackCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Fakes ---

    private class FakeListenTogether : ListenTogetherRepository {
        private val _session = MutableStateFlow<ListenSession?>(null)
        override val currentSession: StateFlow<ListenSession?> = _session.asStateFlow()
        private val _playback = MutableStateFlow<SessionPlayback?>(null)
        override val playback: StateFlow<SessionPlayback?> = _playback.asStateFlow()
        private val _synced = MutableStateFlow(false)
        override val synced: StateFlow<Boolean> = _synced.asStateFlow()
        override val codeLength: Int = 6

        var serverNow: Long? = null
        override fun serverNowUs(): Long? = serverNow

        val setTrackCalls = mutableListOf<SessionTrack>()
        var playCalls = 0
        var pauseCalls = 0
        var seekCalls = mutableListOf<Long>()

        fun setSession(s: ListenSession?) { _session.value = s }
        fun setPlayback(p: SessionPlayback?) { _playback.value = p }
        fun setSynced(v: Boolean) { _synced.value = v }

        override suspend fun startSession(): Result<ListenSession> = Result.failure(NotImplementedError())
        override suspend fun joinSession(codeOrUrl: String): Result<ListenSession> = Result.failure(NotImplementedError())
        override suspend fun leaveSession() { _session.value = null }
        override fun inviteUrlFor(code: String): String = "url/$code"
        override fun parseCode(input: String): String? = input

        override suspend fun controlPlay() { playCalls++ }
        override suspend fun controlPause() { pauseCalls++ }
        override suspend fun controlSeek(positionUs: Long) { seekCalls.add(positionUs) }
        override suspend fun controlSetTrack(track: SessionTrack, positionUs: Long) { setTrackCalls.add(track) }
    }

    private class FakePlayer(
        song: Song? = null,
        playing: Boolean = false,
    ) : PlayerRepository by UnsupportedPlayerRepository() {
        private val _currentSong = MutableStateFlow(song)
        override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()
        private val _playbackState = MutableStateFlow(
            PlaybackInfo(state = if (playing) PlaybackState.PLAYING else PlaybackState.PAUSED)
        )
        override val playbackState: StateFlow<PlaybackInfo> = _playbackState.asStateFlow()
        private val _speed = MutableStateFlow(1f)
        override val playbackSpeed: StateFlow<Float> = _speed.asStateFlow()

        var followerModeEnabled = false
        var position = 0L
        val playRemoteCalls = mutableListOf<MediaId>()

        override suspend fun getCurrentPosition(): Long = position
        override fun setFollowerMode(enabled: Boolean) { followerModeEnabled = enabled }
        override suspend fun setPlaybackSpeed(speed: Float) { _speed.value = speed }
        override suspend fun seekTo(positionMs: Long) { position = positionMs }
        override suspend fun resume() { _playbackState.value = PlaybackInfo(state = PlaybackState.PLAYING) }
        override suspend fun pause() { _playbackState.value = PlaybackInfo(state = PlaybackState.PAUSED) }
        override suspend fun playRemote(
            mediaId: MediaId, title: String, artist: String, artworkUrl: String, playWhenReady: Boolean,
        ) {
            playRemoteCalls.add(mediaId)
            _playbackState.value = PlaybackInfo(state = PlaybackState.PLAYING)
        }
    }

    private fun hostSession() = ListenSession(
        code = "AAA", inviteUrl = "url", hostName = "Me", isHost = true,
        participants = listOf(SessionParticipant("self", "Me", isSelf = true)), canControl = true,
    )

    private fun followerSession() = ListenSession(
        code = "AAA", inviteUrl = "url", hostName = "Host", isHost = false,
        participants = listOf(SessionParticipant("self", "Me", isSelf = true)), canControl = false,
    )

    private fun track() = SessionTrack("testsource", "42", "T", "A", "", "http://art", 200_000)

    private fun playback() = SessionPlayback(
        track = track(), epoch = 1, positionAnchorUs = 0, anchorServerTimeUs = 0,
        rate = 1f, effectiveAtServerTimeUs = 0, controllerId = "",
    )

    private fun song() = Song(
        id = MediaId.Plugin("testsource", "42"), title = "T", artists = listOf(ArtistRef("A")),
        durationMs = 200_000, artworkUrl = "http://art",
    )

    // --- Tests ---

    @Test
    fun noSession_doesNotEnableFollowerMode_norSeed() = runTest {
        val repo = FakeListenTogether()
        val player = FakePlayer()
        val coord = SessionPlaybackCoordinator(repo, player, followerMaxTicks = 1, hostMaxSamples = 1)
        coord.start()
        advanceUntilIdle()

        assertFalse(player.followerModeEnabled)
        assertTrue(repo.setTrackCalls.isEmpty())
    }

    @Test
    fun followerSession_enablesFollowerMode_andLoadsSharedTrack() = runTest {
        val repo = FakeListenTogether()
        val player = FakePlayer()
        val coord = SessionPlaybackCoordinator(repo, player, followerMaxTicks = 1, hostMaxSamples = 1)
        coord.start()
        advanceUntilIdle()

        repo.setSynced(true)
        repo.serverNow = 1_000
        repo.setPlayback(playback())
        repo.setSession(followerSession())
        advanceUntilIdle()

        assertTrue("follower mode should be on", player.followerModeEnabled)
        assertEquals(1, player.playRemoteCalls.size)
        assertEquals(MediaId.Plugin("testsource", "42"), player.playRemoteCalls.first())
        // A follower never seeds the session.
        assertTrue(repo.setTrackCalls.isEmpty())
    }

    @Test
    fun hostSession_seedsSession_andNeverEntersFollowerMode() = runTest {
        val repo = FakeListenTogether()
        val player = FakePlayer(song = song(), playing = true)
        val coord = SessionPlaybackCoordinator(repo, player, followerMaxTicks = 1, hostMaxSamples = 1)
        coord.start()
        advanceUntilIdle()

        repo.setSession(hostSession())
        advanceUntilIdle()

        assertFalse("host must not enter follower mode", player.followerModeEnabled)
        assertEquals(1, repo.setTrackCalls.size)
        assertEquals("testsource", repo.setTrackCalls.first().pluginId)
        assertTrue("host was playing -> controlPlay", repo.playCalls >= 1)
    }

    @Test
    fun leavingSession_disablesFollowerMode() = runTest {
        val repo = FakeListenTogether()
        val player = FakePlayer()
        val coord = SessionPlaybackCoordinator(repo, player, followerMaxTicks = 1, hostMaxSamples = 1)
        coord.start()
        advanceUntilIdle()

        repo.setSynced(true)
        repo.serverNow = 1_000
        repo.setPlayback(playback())
        repo.setSession(followerSession())
        advanceUntilIdle()
        assertTrue(player.followerModeEnabled)

        repo.setSession(null)
        advanceUntilIdle()
        assertFalse("leaving must re-enable autoplay (follower mode off)", player.followerModeEnabled)
    }

    @Test
    fun roleFlip_followerToHost_switchesSides() = runTest {
        val repo = FakeListenTogether()
        val player = FakePlayer(song = song(), playing = false)
        val coord = SessionPlaybackCoordinator(repo, player, followerMaxTicks = 1, hostMaxSamples = 1)
        coord.start()
        advanceUntilIdle()

        // Start as follower.
        repo.setSynced(true)
        repo.serverNow = 1_000
        repo.setPlayback(playback())
        repo.setSession(followerSession())
        advanceUntilIdle()
        assertTrue(player.followerModeEnabled)

        // Promoted to controller.
        repo.setSession(hostSession())
        advanceUntilIdle()

        assertFalse("host mode must turn follower mode off", player.followerModeEnabled)
        assertTrue("host must seed on activation", repo.setTrackCalls.isNotEmpty())
    }
}
