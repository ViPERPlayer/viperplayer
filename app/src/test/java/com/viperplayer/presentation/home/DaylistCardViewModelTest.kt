package com.viperplayer.presentation.home

import com.viperplayer.domain.model.Daylist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReadiness
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.TimeBucket
import com.viperplayer.domain.repository.DaylistRepository
import com.viperplayer.domain.repository.RecommendationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DaylistCardViewModel]'s state derivation: a non-null daylist becomes a Ready card
 * carrying the generated title/description + a capped artwork preview; a null daylist stays Hidden.
 */
class DaylistCardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun song(id: String, art: String? = "art-$id") =
        Song(id = MediaId.Local(id), title = id, artworkUrl = art)

    private fun daylist(songs: List<Song>) = Daylist(
        bucket = TimeBucket.EVENING,
        title = "groovy smooth soul thursday evening",
        description = "A warm mix to unwind into your evening.",
        songs = songs,
        generatedAtMs = 0L,
    )

    @Test
    fun `a daylist becomes a Ready card with a capped artwork preview`() = runTest {
        val songs = (1..8).map { song("s$it") }
        val vm = DaylistCardViewModel(FakeDaylistRepo(daylist(songs)), FakeRec())
        advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s is DaylistCardState.Ready)
        s as DaylistCardState.Ready
        assertEquals("groovy smooth soul thursday evening", s.title)
        assertEquals("A warm mix to unwind into your evening.", s.description)
        // The card previews at most 4 artworks.
        assertEquals(4, s.artworkUrls.size)
    }

    @Test
    fun `songs without artwork are dropped from the preview`() = runTest {
        val songs = listOf(song("a", art = null), song("b"), song("c", art = null), song("d"), song("e"))
        val vm = DaylistCardViewModel(FakeDaylistRepo(daylist(songs)), FakeRec())
        advanceUntilIdle()
        val s = vm.state.value as DaylistCardState.Ready
        assertEquals(listOf("art-b", "art-d", "art-e"), s.artworkUrls)
    }

    @Test
    fun `a null daylist stays hidden`() = runTest {
        val vm = DaylistCardViewModel(FakeDaylistRepo(null), FakeRec())
        advanceUntilIdle()
        assertEquals(DaylistCardState.Hidden, vm.state.value)
    }

    @Test
    fun `onTimeChanged re-derives the card`() = runTest {
        val repo = FakeDaylistRepo(null)
        val vm = DaylistCardViewModel(repo, FakeRec())
        advanceUntilIdle()
        assertEquals(DaylistCardState.Hidden, vm.state.value)

        // A slot rolled over and a daylist is now available; a time tick should surface it.
        repo.daylist = daylist(listOf(song("x"), song("y")))
        vm.onTimeChanged()
        advanceUntilIdle()
        assertTrue(vm.state.value is DaylistCardState.Ready)
    }

    // ---- Fakes ----------------------------------------------------------------------------------

    private class FakeDaylistRepo(var daylist: Daylist?) : DaylistRepository {
        override suspend fun currentDaylist(): Daylist? = daylist
    }

    /** Emits a single "ready" readiness so the card's init collect fires exactly one derivation. */
    private class FakeRec : RecommendationRepository {
        private val readinessFlow = MutableStateFlow(
            RecReadiness(recommendationsEnabled = true, modelReady = true, indexedCount = 10)
        )
        override suspend fun moreLikeThis(seed: MediaId, limit: Int): RecResult =
            RecResult.Empty(RecEmptyReason.NO_RESULTS)
        override suspend fun forYouFromLibrary(limit: Int): RecResult =
            RecResult.Empty(RecEmptyReason.NO_RESULTS)
        override val readiness: Flow<RecReadiness> get() = readinessFlow
        override suspend fun sendFeedback(mediaId: MediaId, positive: Boolean) {}
    }
}
