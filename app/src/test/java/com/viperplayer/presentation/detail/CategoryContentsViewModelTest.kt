package com.viperplayer.presentation.detail

import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PlaybackInfo
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.UnsupportedMediaLibraryRepository
import com.viperplayer.domain.repository.UnsupportedPlayerRepository
import com.viperplayer.domain.repository.UnsupportedPluginRepository
import com.viperplayer.presentation.navigation.CategoryDetail
import com.viperplayer.presentation.search.model.SearchItem
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [CategoryContentsViewModel] over a fake [PluginRepository]: the first page loads and is
 * mapped to search rows, an empty page surfaces the empty state, pagination appends the next page via the
 * plugin cursor (and stops when there's no cursor), and a failed fetch surfaces the error state. Follows
 * the fakes + [StandardTestDispatcher] pattern of the other detail-VM tests.
 */
class CategoryContentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val category = CategoryDetail(pluginId = "othersource", categoryId = "moods", name = "Moods")

    private val songA = Song(
        id = MediaId.Plugin("othersource", "a"),
        title = "Alpha",
        artworkUrl = "https://art/a.jpg",
    )
    private val songB = Song(id = MediaId.Plugin("othersource", "b"), title = "Beta")
    private val albumC = Album(id = MediaId.Plugin("othersource", "c"), name = "Gamma")
    private val artistD = Artist(
        id = MediaId.Plugin("othersource", "d"),
        name = "Delta",
        imageUrl = "https://art/d.jpg",
    )

    private fun viewModel(plugin: PluginRepository) = CategoryContentsViewModel(
        category = category,
        pluginRepository = plugin,
        mediaLibraryRepository = InertMediaLibraryRepository(),
        playerRepository = InertPlayerRepository(),
        stringProvider = { _, _ -> "res" },
    )

    @Test
    fun load_firstPage_mapsItemsToSuccess() = runTest {
        val plugin = ScriptedPluginRepository(
            pages = mapOf(null to SearchResult(items = listOf(songA, albumC, artistD), nextCursor = null))
        )
        val vm = viewModel(plugin)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Success", state is CategoryContentsUiState.Success)
        val items = (state as CategoryContentsUiState.Success).items
        assertEquals(listOf(songA.id, albumC.id, artistD.id), items.map { it.id })
        assertEquals(
            listOf(SearchItem.Type.SONG, SearchItem.Type.ALBUM, SearchItem.Type.ARTIST),
            items.map { it.type },
        )
        // Fields are actually mapped (not just id/type): song title + artwork, and — the field most
        // prone to a mapping mistake — an Artist's imageUrl becomes the row's artworkUrl.
        assertEquals("Alpha", items[0].title)
        assertEquals("https://art/a.jpg", items[0].artworkUrl)
        assertEquals("Delta", items[2].title)
        assertEquals("https://art/d.jpg", items[2].artworkUrl)
        assertEquals(1, plugin.callCount)
    }

    @Test
    fun load_emptyPage_surfacesEmptyState() = runTest {
        val plugin = ScriptedPluginRepository(
            pages = mapOf(null to SearchResult(items = emptyList(), nextCursor = null))
        )
        val vm = viewModel(plugin)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is CategoryContentsUiState.Empty)
    }

    @Test
    fun loadMore_appendsNextPage_usingCursor() = runTest {
        val plugin = ScriptedPluginRepository(
            pages = mapOf(
                null to SearchResult(items = listOf(songA), nextCursor = "p2"),
                "p2" to SearchResult(items = listOf(songB), nextCursor = null),
            )
        )
        val vm = viewModel(plugin)
        advanceUntilIdle()
        assertEquals(listOf(songA.id), (vm.uiState.value as CategoryContentsUiState.Success).items.map { it.id })

        vm.loadMore()
        advanceUntilIdle()

        val items = (vm.uiState.value as CategoryContentsUiState.Success).items
        assertEquals(listOf(songA.id, songB.id), items.map { it.id })
        assertEquals("first page + one more page", 2, plugin.callCount)

        // No cursor remains — a further loadMore must be a no-op (no extra fetch).
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(2, plugin.callCount)
    }

    @Test
    fun load_emptyFirstPageWithCursor_keepsPagingUntilItemsArrive() = runTest {
        // First page is empty but has a cursor to a page that does have items: pagination must not
        // dead-end on Empty; it should keep going and end on the page with content.
        val plugin = ScriptedPluginRepository(
            pages = mapOf(
                null to SearchResult(items = emptyList(), nextCursor = "p2"),
                "p2" to SearchResult(items = listOf(songA), nextCursor = null),
            )
        )
        val vm = viewModel(plugin)
        advanceUntilIdle()
        // The empty-but-more first page is a (momentarily empty) Success, not the terminal Empty.
        assertTrue(vm.uiState.value is CategoryContentsUiState.Success)

        vm.loadMore()
        advanceUntilIdle()

        val items = (vm.uiState.value as CategoryContentsUiState.Success).items
        assertEquals(listOf(songA.id), items.map { it.id })
    }

    @Test
    fun getArtist_resolvesAndSaves_tappedArtistForOptionsSheet() = runTest {
        val plugin = ScriptedPluginRepository(
            pages = mapOf(null to SearchResult(items = listOf(artistD), nextCursor = null)),
            artists = mapOf(artistD.id to ArtistDetail(id = artistD.id, name = "Delta", imageUrl = "x")),
        )
        val media = InertMediaLibraryRepository()
        val vm = CategoryContentsViewModel(
            category = category,
            pluginRepository = plugin,
            mediaLibraryRepository = media,
            playerRepository = InertPlayerRepository(),
            stringProvider = { _, _ -> "res" },
        )
        advanceUntilIdle()

        val artist = vm.getArtist(artistD.id)
        assertEquals(artistD.id, artist?.id)
        assertEquals("Delta", artist?.name)
        assertEquals("resolved artist is persisted", listOf(artistD.id), media.savedArtistIds)
    }

    @Test
    fun load_failure_surfacesErrorState() = runTest {
        val plugin = ScriptedPluginRepository(pages = emptyMap(), failure = IllegalStateException("boom"))
        val vm = viewModel(plugin)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("expected Error", state is CategoryContentsUiState.Error)
        assertEquals("boom", (state as CategoryContentsUiState.Error).message)
    }

    /** Serves scripted [SearchResult] pages keyed by the requested cursor; counts fetches. */
    private class ScriptedPluginRepository(
        private val pages: Map<String?, SearchResult>,
        private val failure: Throwable? = null,
        private val artists: Map<MediaId, ArtistDetail> = emptyMap(),
    ) : PluginRepository by UnsupportedPluginRepository() {
        var callCount = 0
            private set

        override suspend fun getCategoryContents(
            pluginId: String,
            categoryId: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchResult> {
            callCount++
            failure?.let { return Result.failure(it) }
            return Result.success(pages.getValue(cursor))
        }

        override suspend fun getArtist(mediaId: MediaId): Result<ArtistDetail> =
            artists[mediaId]?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException("no artist $mediaId"))
    }

    private class InertPlayerRepository : PlayerRepository by UnsupportedPlayerRepository() {
        override val playbackState: StateFlow<PlaybackInfo> = MutableStateFlow(PlaybackInfo())
        override val currentSong: StateFlow<Song?> = MutableStateFlow(null)
    }

    private class InertMediaLibraryRepository :
        MediaLibraryRepository by UnsupportedMediaLibraryRepository() {
        val savedArtistIds = mutableListOf<MediaId>()

        override suspend fun saveSong(song: Song) = Unit
        override suspend fun saveAlbum(album: Album) = Unit
        override suspend fun saveArtist(artist: ArtistDetail) {
            savedArtistIds.add(artist.id)
        }
    }
}
