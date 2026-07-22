package com.viperplayer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.viperplayer.data.local.ViperPlayerDatabase
import com.viperplayer.data.source.LocalMediaDataSource
import com.viperplayer.data.sync.push.LibraryPushOutbox
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.ArtistDetail
import com.viperplayer.domain.model.BrowseCategory
import com.viperplayer.domain.model.HomeContent
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.PagedResult
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Plugin
import com.viperplayer.domain.model.PluginInfo
import com.viperplayer.domain.model.PluginPendingAction
import com.viperplayer.domain.model.SearchFilter
import com.viperplayer.domain.model.SearchResult
import com.viperplayer.domain.model.SearchSuggestions
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.PluginRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for the local-playlist mutations on [MediaLibraryRepositoryImpl], run against a
 * real in-memory Room database so they exercise the actual `playlist_songs` order column via the
 * CrossRefDao. Covers add (append), remove (compaction) and reorder ordering.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistMutationTest {

    private lateinit var db: ViperPlayerDatabase
    private lateinit var repository: MediaLibraryRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ViperPlayerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MediaLibraryRepositoryImpl(
            artistDao = db.artistDao(),
            albumDao = db.albumDao(),
            songDao = db.songDao(),
            playlistDao = db.playlistDao(),
            genreDao = db.genreDao(),
            crossRefDao = db.crossRefDao(),
            playEventDao = db.playEventDao(),
            pluginRepository = FakePluginRepository(),
            artworkDownloader = ArtworkDownloader(context),
            networkConnectivityChecker = NetworkConnectivityChecker(context),
            localMediaDataSource = LocalMediaDataSource(context),
            libraryPushOutbox = LibraryPushOutbox.NOOP,
            stringProvider = { id, args -> context.getString(id, *args) },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun song(id: String, title: String = id) =
        Song(id = MediaId.Local(id), title = title, durationMs = 1000L)

    /** The current ordered song source-ids for a playlist (via the reactive getPlaylist flow). */
    private suspend fun orderedIds(playlistId: MediaId): List<String> =
        repository.getPlaylist(playlistId).first()?.songs?.map { it.id.sourceId } ?: emptyList()

    @Test
    fun addSongToPlaylist_appendsInInsertionOrder() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")

        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))
        repository.addSongToPlaylist(playlistId, song("c"))

        assertEquals(listOf("a", "b", "c"), orderedIds(playlistId))
    }

    @Test
    fun addSongToPlaylist_duplicate_isIgnored() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")

        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))
        // Re-adding "a" must not duplicate it nor move it.
        repository.addSongToPlaylist(playlistId, song("a"))

        assertEquals(listOf("a", "b"), orderedIds(playlistId))
    }

    @Test
    fun addSongToPlaylist_duplicate_doesNotClobberPersistedMetadata() = runBlocking {
        // Re-adding a song that is already a member must not run saveSong again — otherwise a leaner
        // Song (e.g. a plugin track missing its title/artwork) would overwrite the good persisted row.
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a", title = "Full Title"))

        // Re-add the same id but with a blank/leaner title; the stored title must be preserved.
        repository.addSongToPlaylist(playlistId, song("a", title = ""))

        val stored = repository.getPlaylist(playlistId).first()?.songs?.first { it.id.sourceId == "a" }
        assertNotNull(stored)
        assertEquals("Full Title", stored!!.title)
    }

    @Test
    fun createLocalPlaylist_backToBack_producesDistinctPlaylists() = runBlocking {
        // Two creations in the same millisecond must not collide (PlaylistEntity has a UNIQUE
        // index on (pluginId, sourceId) with REPLACE) — otherwise the first playlist is silently
        // replaced and its songs cascade-deleted.
        val first = repository.createLocalPlaylist("First")
        val second = repository.createLocalPlaylist("Second")

        assertNotEquals(first.sourceId, second.sourceId)
        repository.addSongToPlaylist(first, song("a"))
        repository.addSongToPlaylist(second, song("b"))

        assertEquals(listOf("a"), orderedIds(first))
        assertEquals(listOf("b"), orderedIds(second))

        val names = repository.getLocalPlaylists().first().map { it.name }
        assertEquals(true, names.contains("First"))
        assertEquals(true, names.contains("Second"))
    }

    @Test
    fun removeSongFromPlaylist_compactsRemainingOrder() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))
        repository.addSongToPlaylist(playlistId, song("c"))

        repository.removeSongFromPlaylist(playlistId, MediaId.Local("b"))

        assertEquals(listOf("a", "c"), orderedIds(playlistId))
        // After compaction a newly-added song must land at the end (position 2), not collide with "c".
        repository.addSongToPlaylist(playlistId, song("d"))
        assertEquals(listOf("a", "c", "d"), orderedIds(playlistId))
    }

    @Test
    fun reorderPlaylistSongs_movesForward() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))
        repository.addSongToPlaylist(playlistId, song("c"))
        repository.addSongToPlaylist(playlistId, song("d"))

        // Move "a" (0) to position 2 -> [b, c, a, d]
        repository.reorderPlaylistSongs(playlistId, fromIndex = 0, toIndex = 2)

        assertEquals(listOf("b", "c", "a", "d"), orderedIds(playlistId))
    }

    @Test
    fun reorderPlaylistSongs_movesBackward() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))
        repository.addSongToPlaylist(playlistId, song("c"))
        repository.addSongToPlaylist(playlistId, song("d"))

        // Move "d" (3) to position 1 -> [a, d, b, c]
        repository.reorderPlaylistSongs(playlistId, fromIndex = 3, toIndex = 1)

        assertEquals(listOf("a", "d", "b", "c"), orderedIds(playlistId))
    }

    @Test
    fun reorderPlaylistSongs_outOfRange_isNoOp() = runBlocking {
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))

        repository.reorderPlaylistSongs(playlistId, fromIndex = 0, toIndex = 9)

        assertEquals(listOf("a", "b"), orderedIds(playlistId))
    }

    @Test
    fun getPlaylist_resolvesCreatedLocalPlaylist() = runBlocking {
        // Mirrors the PlaylistDetail load path for a user-created local playlist: the detail screen
        // routes editable local playlists through getPlaylist() (not the plugin), so this must resolve
        // to a non-null playlist with its songs — otherwise the screen can never reach Success.
        val playlistId = repository.createLocalPlaylist("My List")
        repository.addSongToPlaylist(playlistId, song("a", title = "Song A"))
        repository.addSongToPlaylist(playlistId, song("b", title = "Song B"))

        val playlist = repository.getPlaylist(playlistId).first()

        assertNotNull(playlist)
        assertEquals("My List", playlist!!.name)
        assertEquals(listOf("a", "b"), playlist.songs?.map { it.id.sourceId })
    }

    @Test
    fun renamePlaylist_persistsNewName() = runBlocking {
        val playlistId = repository.createLocalPlaylist("Old Name")

        repository.renamePlaylist(playlistId, "New Name")

        // The renamed name must be reflected both on the single-playlist flow and the local list, and
        // renaming must not disturb the playlist's songs.
        repository.addSongToPlaylist(playlistId, song("a"))
        val playlist = repository.getPlaylist(playlistId).first()
        assertNotNull(playlist)
        assertEquals("New Name", playlist!!.name)
        assertEquals(listOf("a"), playlist.songs?.map { it.id.sourceId })

        val listed = repository.getLocalPlaylists().first().first { it.id == playlistId }
        assertEquals("New Name", listed.name)
    }

    @Test
    fun renamePlaylist_trimsWhitespace() = runBlocking {
        val playlistId = repository.createLocalPlaylist("Original")

        repository.renamePlaylist(playlistId, "  Padded Name  ")

        assertEquals("Padded Name", repository.getPlaylist(playlistId).first()?.name)
    }

    @Test
    fun renamePlaylist_blankName_isNoOp() = runBlocking {
        val playlistId = repository.createLocalPlaylist("Keep Me")

        repository.renamePlaylist(playlistId, "   ")

        // A blank name must not clobber the existing name.
        assertEquals("Keep Me", repository.getPlaylist(playlistId).first()?.name)
    }

    @Test
    fun renamePlaylist_nonLocalPlaylist_isNoOp() = runBlocking {
        // Guard: only local playlists are renameable. A remote plugin playlist id has no local row,
        // so the update simply affects nothing (and never throws).
        repository.renamePlaylist(MediaId.Plugin("testsource", "remote123"), "Hacked")
        // Sanity: an unrelated local playlist is untouched.
        val local = repository.createLocalPlaylist("Safe")
        repository.renamePlaylist(MediaId.Plugin("testsource", "remote123"), "Again")
        assertEquals("Safe", repository.getPlaylist(local).first()?.name)
    }

    @Test
    fun getLocalPlaylists_reflectsCreatedPlaylistsWithCounts() = runBlocking {
        val playlistId = repository.createLocalPlaylist("Road Trip")
        repository.addSongToPlaylist(playlistId, song("a"))
        repository.addSongToPlaylist(playlistId, song("b"))

        val playlists = repository.getLocalPlaylists().first()
        val created = playlists.first { it.id == playlistId }
        assertEquals("Road Trip", created.name)
        assertEquals(2, created.songCount)
    }
}

/** Minimal [PluginRepository] fake: no plugins connected; unused methods are never called in tests. */
private class FakePluginRepository : PluginRepository {
    override val discoveredPlugins: Flow<List<PluginInfo>> = flowOf(emptyList())
    override val connectedPlugins: Flow<List<Plugin>> = flowOf(emptyList())
    override val disabledPlugins: Flow<Set<String>> = flowOf(emptySet())
    override val pendingActions: Flow<List<PluginPendingAction>> = flowOf(emptyList())

    override suspend fun refreshPluginStatuses() = Unit
    override suspend fun refreshPlugins() = Unit
    override suspend fun enablePlugin(pluginId: String): Result<Unit> = Result.success(Unit)
    override suspend fun disablePlugin(pluginId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getSearchSuggestions(query: String): Flow<List<Result<SearchSuggestions>>> =
        flowOf(emptyList())

    override suspend fun search(query: String, filter: SearchFilter?, cursor: String?, limit: Int): Result<SearchResult> =
        error("unused")

    override suspend fun getHomeContent(): Result<List<Pair<String, HomeContent>>> = error("unused")
    override suspend fun getBrowseCategories(cursor: String?, limit: Int): Result<PagedResult<BrowseCategory>> =
        error("unused")

    override suspend fun getCategoryContents(pluginId: String, categoryId: String, cursor: String?, limit: Int): Result<SearchResult> =
        error("unused")

    override suspend fun filterSection(pluginId: String, sectionId: String, filterKey: String): Result<SearchResult> =
        error("unused")

    override suspend fun getLibrarySongs(cursor: String?, limit: Int): Result<PagedResult<Song>> = error("unused")
    override suspend fun getLibraryAlbums(cursor: String?, limit: Int): Result<PagedResult<Album>> = error("unused")
    override suspend fun getLibraryArtists(cursor: String?, limit: Int): Result<PagedResult<Artist>> = error("unused")
    override suspend fun getLibraryPlaylists(cursor: String?, limit: Int): Result<PagedResult<Playlist>> = error("unused")
    override suspend fun getLibrarySongs(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Song>> = error("unused")
    override suspend fun getLibraryAlbums(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Album>> = error("unused")
    override suspend fun getLibraryArtists(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Artist>> = error("unused")
    override suspend fun getLibraryPlaylists(pluginId: String, cursor: String?, limit: Int): Result<PagedResult<Playlist>> = error("unused")
    override suspend fun getSong(mediaId: MediaId): Result<Song> = error("unused")
    override suspend fun getAlbum(mediaId: MediaId): Result<Album> = error("unused")
    override suspend fun getArtist(mediaId: MediaId): Result<ArtistDetail> = error("unused")
    override suspend fun getPlaylist(mediaId: MediaId): Result<Playlist> = error("unused")
    override suspend fun getLyrics(song: Song): Result<Lyrics?> = error("unused")
    override suspend fun getArtistSongs(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> = error("unused")
    override suspend fun getArtistAlbums(artistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Album>> = error("unused")
    override suspend fun getPlaylistSongs(playlistId: MediaId, cursor: String?, limit: Int): Result<PagedResult<Song>> = error("unused")
    override suspend fun getRelatedSongs(songId: MediaId): Result<PagedResult<Song>> = error("unused")
}
