package com.viperplayer.presentation.rec

import com.viperplayer.domain.model.Daylist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackContext
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.TimeBucket
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Unit tests for [DaylistViewModel]'s state mapping: a non-null daylist becomes a titled, non-loading
 * [RecUiState] with the generated title/description; a null daylist becomes a graceful empty state; and
 * playback routes through the player under [PlaybackContext.Daylist].
 */
class DaylistViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun song(id: String) = Song(id = MediaId.Local(id), title = id)

    private fun daylist(songs: List<Song>) = Daylist(
        bucket = TimeBucket.MORNING,
        title = "fresh four-on-the-floor wednesday morning",
        description = "An easy mix to ease into your morning.",
        songs = songs,
        generatedAtMs = 0L,
    )

    @Test
    fun `a daylist maps into a titled non-loading state`() = runTest {
        val vm = DaylistViewModel(FakeDaylistRepo(daylist(listOf(song("a"), song("b")))), RecordingPlayer())
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertEquals("fresh four-on-the-floor wednesday morning", s.title)
        assertEquals("An easy mix to ease into your morning.", s.subtitle)
        assertEquals(listOf("a", "b"), s.songs.map { it.title })
        assertTrue(s.hasSongs)
    }

    @Test
    fun `a null daylist maps into a graceful empty state`() = runTest {
        val vm = DaylistViewModel(FakeDaylistRepo(null), RecordingPlayer())
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertFalse(s.hasSongs)
        assertEquals(RecEmptyReason.NO_RESULTS, s.emptyReason)
    }

    @Test
    fun `playAll routes through the player under the Daylist context`() = runTest {
        val player = RecordingPlayer()
        val vm = DaylistViewModel(FakeDaylistRepo(daylist(listOf(song("a"), song("b")))), player)
        advanceUntilIdle()

        vm.playAll()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), player.playedAll?.map { it.title })
        assertEquals(0, player.startIndex)
        assertTrue(player.context is PlaybackContext.Daylist)
    }

    @Test
    fun `playSong seeds the queue at the tapped song`() = runTest {
        val player = RecordingPlayer()
        val vm = DaylistViewModel(FakeDaylistRepo(daylist(listOf(song("a"), song("b"), song("c")))), player)
        advanceUntilIdle()

        vm.playSong(song("b"))
        advanceUntilIdle()

        assertEquals(1, player.startIndex)
        assertTrue(player.context is PlaybackContext.Daylist)
    }

    // ---- Fakes ----------------------------------------------------------------------------------

    private class FakeDaylistRepo(private val daylist: Daylist?) : DaylistRepository {
        override suspend fun currentDaylist(): Daylist? = daylist
    }

    private class RecordingPlayer : PlayerRepository by UnsupportedPlayerRepository() {
        var playedAll: List<Song>? = null
        var startIndex: Int = -1
        var context: PlaybackContext? = null

        override val playbackState: StateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())
        override val currentSong: StateFlow<Song?> = MutableStateFlow(null)

        override suspend fun playAll(songs: List<Song>, startIndex: Int, context: PlaybackContext?) {
            playedAll = songs
            this.startIndex = startIndex
            this.context = context
        }
    }
}
