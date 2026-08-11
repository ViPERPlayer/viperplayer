package com.viperplayer.data.repository

import com.viperplayer.domain.account.AccountApiResult
import com.viperplayer.data.rec.CandidateDto
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.DiscoverResponseDto
import com.viperplayer.data.rec.RecommendationApi
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.account.AccountUser
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.Interaction
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.rec.TasteState
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedSettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DiscoveryRepositoryImpl] over hand-written fakes (fake api + taste + settings + owned
 * library + account seam): the opt-in and warm-taste gates, the candidate→[Song] mapping onto
 * [MediaId.Plugin], the exclude-id building from the owned library ("pluginId:sourceId", locals
 * skipped), a graceful empty on a 409 handshake mismatch, and contribute-only-when-opted-in.
 */
class DiscoveryRepositoryImplTest {

    private fun vector(x: Float, y: Float): FloatArray = FloatArray(ClapModel.EMBEDDING_DIM).also {
        it[0] = x
        it[1] = y
    }

    private fun plugin(pluginId: String, sourceId: String) = MediaId.Plugin(pluginId, sourceId)

    private fun ownedSong(pluginId: String, sourceId: String) =
        Song(id = plugin(pluginId, sourceId), title = sourceId)

    private fun localSong(sourceId: String) = Song(id = MediaId.Local(sourceId), title = sourceId)

    private fun candidate(pluginId: String, sourceId: String, title: String = sourceId, artist: String = "") =
        CandidateDto(pluginId = pluginId, sourceId = sourceId, title = title, artist = artist)

    // --- gates ---

    @Test
    fun discover_recommendationsDisabled_returnsDisabledEmpty() = runTest {
        val repo = repo(recommendationsEnabled = false)

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.RECOMMENDATIONS_DISABLED, (result as RecResult.Empty).reason)
    }

    @Test
    fun discover_coldTaste_returnsNotIndexedEmpty() = runTest {
        val repo = repo(taste = FakeTaste(TasteState.COLD))

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.NOT_INDEXED_YET, (result as RecResult.Empty).reason)
    }

    // --- happy path: candidate → Song mapping + exclude-id building ---

    @Test
    fun discover_mapsCandidatesToPluginSongs_asFallbackResult() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(
                    modelVersion = ClapModel.MODEL_VERSION,
                    candidates = listOf(
                        candidate("testsource", "t1", title = "Song A", artist = "Artist A"),
                        candidate("othersource", "y2", title = "Song B"),
                    ),
                )
            )
        )
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        val result = repo.discover(limit = 10)

        assertTrue(result is RecResult.Fallback)
        val songs = (result as RecResult.Fallback).songs
        assertEquals(listOf(plugin("testsource", "t1"), plugin("othersource", "y2")), songs.map { it.id })
        assertEquals("Song A", songs[0].title)
        assertEquals("Artist A", songs[0].artistNames)
        // The vector actually sent to the backend is the warm taste vector, model-versioned.
        assertEquals(ClapModel.MODEL_VERSION, api.lastModelVersion)
        assertEquals(1f, api.lastTasteVector!![0])
    }

    @Test
    fun discover_buildsExcludeIds_fromOwnedLibrary_pluginOnly_deduped() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(ClapModel.MODEL_VERSION, listOf(candidate("testsource", "new1")))
            )
        )
        val media = FakeMedia(
            saved = listOf(ownedSong("testsource", "s1"), localSong("local1"), ownedSong("testsource", "s2")),
            liked = listOf(ownedSong("testsource", "s1"), ownedSong("fourthsource", "d9")), // s1 dup, local skipped
        )
        val repo = repo(api = api, media = media, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        repo.discover(limit = 10)

        // "pluginId:sourceId", plugin-only, deduped, order-preserving (saved first then liked).
        assertEquals(listOf("testsource:s1", "testsource:s2", "fourthsource:d9"), api.lastExcludeIds)
    }

    @Test
    fun discover_dropsBlankCandidates() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(
                    ClapModel.MODEL_VERSION,
                    listOf(
                        candidate("testsource", "t1", title = "Good"),
                        candidate("", "x", title = "No plugin"),      // blank pluginId → dropped
                        candidate("testsource", "y", title = ""),           // blank title → dropped
                    ),
                )
            )
        )
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        val result = repo.discover(limit = 10)

        assertEquals(listOf(plugin("testsource", "t1")), (result as RecResult.Fallback).songs.map { it.id })
    }

    // --- graceful degradation ---

    @Test
    fun discover_handshakeMismatch_returnsGracefulEmpty_notError() = runTest {
        val api = FakeApi(discoverResult = RecommendationApi.DiscoverResult.HandshakeMismatch)
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.NO_RESULTS, (result as RecResult.Empty).reason)
    }

    @Test
    fun discover_networkError_returnsGracefulEmpty() = runTest {
        val api = FakeApi(discoverResult = RecommendationApi.DiscoverResult.Error)
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.NO_RESULTS, (result as RecResult.Empty).reason)
    }

    @Test
    fun discover_emptyCandidates_returnsNoResults() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(ClapModel.MODEL_VERSION, emptyList())
            )
        )
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.NO_RESULTS, (result as RecResult.Empty).reason)
    }

    @Test
    fun discover_noSession_returnsGracefulEmpty() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(ClapModel.MODEL_VERSION, listOf(candidate("testsource", "t1")))
            )
        )
        // No token → withBackendAuth short-circuits to Unauthenticated → graceful empty (never throws).
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))), token = null)

        val result = repo.discover(limit = 10)

        assertEquals(RecEmptyReason.NO_RESULTS, (result as RecResult.Empty).reason)
    }

    // --- contribute ---

    @Test
    fun contribute_optedOut_doesNotCallApi() = runTest {
        val api = FakeApi()
        val repo = repo(api = api, contributeEnabled = false, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))

        repo.contributeTasteIfEnabled()

        assertEquals(0, api.contributeCalls)
    }

    @Test
    fun contribute_optedInWithWarmTaste_sendsSingleVector() = runTest {
        val api = FakeApi(contributeResult = RecommendationApi.ContributeResult.Success(1))
        val repo = repo(api = api, contributeEnabled = true, taste = FakeTaste(TasteState(vector = vector(0f, 1f))))

        repo.contributeTasteIfEnabled()

        assertEquals(1, api.contributeCalls)
        assertEquals(1, api.lastContributeVectors!!.size)
        assertEquals(1f, api.lastContributeVectors!![0][1])
        assertEquals(ClapModel.MODEL_VERSION, api.lastContributeModelVersion)
    }

    @Test
    fun contribute_optedInButColdTaste_doesNotCallApi() = runTest {
        val api = FakeApi()
        val repo = repo(api = api, contributeEnabled = true, taste = FakeTaste(TasteState.COLD))

        repo.contributeTasteIfEnabled()

        assertEquals(0, api.contributeCalls)
    }

    @Test
    fun contribute_recommendationsDisabled_doesNotCallApi() = runTest {
        // The privacy gate: even opted-in with a warm taste, NOTHING is contributed unless the
        // Smart-recommendations master opt-in is on. This is what makes the ON-by-default contribute
        // setting safe — deleting/reordering the recommendationsEnabled short-circuit turns this red.
        val api = FakeApi()
        val repo = repo(
            api = api,
            recommendationsEnabled = false,
            contributeEnabled = true,
            taste = FakeTaste(TasteState(vector = vector(1f, 0f))),
        )

        repo.contributeTasteIfEnabled()

        assertEquals(0, api.contributeCalls)
    }

    // --- feedback (P4) ---

    @Test
    fun discover_excludesThumbedDownIds() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(
                    ClapModel.MODEL_VERSION,
                    listOf(candidate("testsource", "t1"), candidate("testsource", "t2")),
                )
            )
        )
        // t1 was previously suppressed → excluded from the feed; t2 remains.
        val taste = FakeTaste(TasteState(vector = vector(1f, 0f)), initialSuppressed = setOf("testsource:t1"))
        val repo = repo(api = api, taste = taste)

        val result = repo.discover(limit = 10) as RecResult.Fallback
        assertEquals(listOf(plugin("testsource", "t2")), result.songs.map { it.id })
    }

    @Test
    fun sendFeedback_thumbsDown_suppressesIdForNextFetch() = runTest {
        val candidates = listOf(candidate("testsource", "t1"), candidate("testsource", "t2"))
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(ClapModel.MODEL_VERSION, candidates)
            )
        )
        val taste = FakeTaste(TasteState(vector = vector(1f, 0f)))
        val repo = repo(api = api, taste = taste)

        // Thumbs-down t1, then re-fetch: t1 no longer appears.
        repo.sendFeedback(plugin("testsource", "t1"), positive = false)
        assertTrue("t1 suppressed", "testsource:t1" in taste.suppressed)
        val result = repo.discover(limit = 10) as RecResult.Fallback
        assertEquals(listOf(plugin("testsource", "t2")), result.songs.map { it.id })
    }

    @Test
    fun sendFeedback_thumbsUp_likesTheSong() = runTest {
        val media = RecordingMedia()
        val repo = repo(media = media, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))
        repo.sendFeedback(plugin("testsource", "t1"), positive = true)
        assertEquals(listOf(plugin("testsource", "t1") to true), media.liked)
    }

    @Test
    fun discover_attachesMatchesTasteReason() = runTest {
        val api = FakeApi(
            discoverResult = RecommendationApi.DiscoverResult.Success(
                DiscoverResponseDto(ClapModel.MODEL_VERSION, listOf(candidate("testsource", "t1")))
            )
        )
        val repo = repo(api = api, taste = FakeTaste(TasteState(vector = vector(1f, 0f))))
        val result = repo.discover(limit = 10) as RecResult.Fallback
        assertEquals(RecReason.Kind.MATCHES_TASTE, result.reasons[plugin("testsource", "t1")]?.kind)
    }

    // --- wiring ------------------------------------------------------------------------------------

    private fun repo(
        api: RecommendationApi = FakeApi(),
        media: MediaLibraryRepository = FakeMedia(),
        taste: TasteRepository = FakeTaste(TasteState(vector = vector(1f, 0f))),
        recommendationsEnabled: Boolean = true,
        contributeEnabled: Boolean = false,
        token: String? = "tok",
    ) = DiscoveryRepositoryImpl(
        recommendationApi = api,
        accountRepository = FakeAccount(token),
        settingsRepository = FakeSettings(recommendationsEnabled, contributeEnabled),
        tasteRepository = taste,
        mediaLibraryRepository = media,
    )

    /** A fake [RecommendationApi]: overrides both calls, recording args. Super engine is never hit. */
    private class FakeApi(
        private val discoverResult: RecommendationApi.DiscoverResult =
            RecommendationApi.DiscoverResult.Success(DiscoverResponseDto(ClapModel.MODEL_VERSION, emptyList())),
        private val contributeResult: RecommendationApi.ContributeResult =
            RecommendationApi.ContributeResult.Success(0),
    ) : RecommendationApi(HttpClient(MockEngine { error("engine must not be hit") })) {
        var lastModelVersion: String? = null
        var lastTasteVector: FloatArray? = null
        var lastExcludeIds: List<String>? = null
        var contributeCalls = 0
        var lastContributeModelVersion: String? = null
        var lastContributeVectors: List<FloatArray>? = null

        override suspend fun discover(
            accessToken: String,
            modelVersion: String,
            tasteVector: FloatArray,
            excludeIds: List<String>,
            limit: Int,
        ): DiscoverResult {
            lastModelVersion = modelVersion
            lastTasteVector = tasteVector
            lastExcludeIds = excludeIds
            return discoverResult
        }

        override suspend fun contribute(
            accessToken: String,
            modelVersion: String,
            vectors: List<FloatArray>,
        ): ContributeResult {
            contributeCalls++
            lastContributeModelVersion = modelVersion
            lastContributeVectors = vectors
            return contributeResult
        }
    }

    private class FakeMedia(
        private val saved: List<Song> = emptyList(),
        private val liked: List<Song> = emptyList(),
    ) : MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        override fun getAllSavedSongs(): Flow<List<Song>> = flowOf(saved)
        override fun getAllLikedSongs(): Flow<List<Song>> = flowOf(liked)
    }

    /** Records the (id, isLiked) pairs passed to [setSongLiked] so a feedback test can assert the like. */
    private class RecordingMedia : MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        val liked = mutableListOf<Pair<MediaId, Boolean>>()
        override suspend fun setSongLiked(mediaId: MediaId, isLiked: Boolean) { liked += mediaId to isLiked }
    }

    private class FakeSettings(
        private val recommendations: Boolean,
        private val contribute: Boolean,
    ) : SettingsRepository by UnsupportedSettingsRepository() {
        override val recommendationsEnabled: Flow<Boolean> get() = flowOf(recommendations)
        override val contributeAnonymizedTaste: Flow<Boolean> get() = flowOf(contribute)
    }

    /** A fake account seam whose withBackendAuth supplies [token] and delegates (Unauthenticated when null). */
    private class FakeAccount(private val token: String?) : AccountRepository {
        override val state: Flow<AccountState> = emptyFlow()
        override val isConfigured: Boolean = true
        override suspend fun register(email: String, password: String, displayName: String?) =
            throw UnsupportedOperationException()
        override suspend fun login(email: String, password: String) = throw UnsupportedOperationException()
        override suspend fun changePassword(current: String, new: String) = throw UnsupportedOperationException()
        override suspend fun deleteAccount(password: String) = throw UnsupportedOperationException()
        override suspend fun setHandle(handle: String) = throw UnsupportedOperationException()
        override suspend fun refreshProfile(): AccountUser? = null
        override suspend fun logout() {}
        override suspend fun validAccessToken(): String? = token
        override suspend fun <T> withBackendAuth(
            call: suspend (accessToken: String) -> AccountApiResult<T>,
        ): AccountApiResult<T> {
            val t = token ?: return AccountApiResult.Unauthenticated
            return call(t)
        }
    }

    /** Test [TasteRepository]: returns a fixed taste and records/serves the discovery suppression set. */
    private class FakeTaste(
        private val state: TasteState,
        initialSuppressed: Set<String> = emptySet(),
    ) : TasteRepository {
        val suppressed = initialSuppressed.toMutableSet()
        override suspend fun taste(): TasteState = state
        override suspend fun currentBucketTaste(): TasteState = state
        override suspend fun moodCentroids(): List<FloatArray> = emptyList()
        override suspend fun recordServed(servedIds: List<Long>) = Unit
        override suspend fun servedRecency(): Map<Long, Float> = emptyMap()
        override suspend fun servedCounts(): Map<Long, Int> = emptyMap()
        override suspend fun onInteraction(interaction: Interaction) = Unit
        override suspend fun suppressDiscoveryId(id: String) { suppressed += id }
        override suspend fun suppressedDiscoveryIds(): Set<String> = suppressed
        override suspend fun invalidate() = Unit
    }
}
