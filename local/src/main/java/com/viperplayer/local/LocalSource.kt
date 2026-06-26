package com.viperplayer.local

import android.content.Context
import com.viperplayer.local.data.LocalMediaScanner
import com.viperplayer.local.mapper.LocalMapper
import com.viperplayer.plugin.model.Album
import com.viperplayer.plugin.model.Artist
import com.viperplayer.plugin.model.MediaItem
import com.viperplayer.plugin.model.MediaType
import com.viperplayer.plugin.model.Page
import com.viperplayer.plugin.model.PageRequest
import com.viperplayer.plugin.model.Playlist
import com.viperplayer.plugin.model.PluginErrorCode
import com.viperplayer.plugin.model.PluginException
import com.viperplayer.plugin.model.SearchRequest
import com.viperplayer.plugin.model.SearchResult
import com.viperplayer.plugin.model.Song
import com.viperplayer.plugin.author.SourceProvider
import com.viperplayer.plugin.author.StreamResponse

/**
 * [SourceProvider] backed by on-device MediaStore audio. Reuses [LocalMediaScanner] for the actual
 * scanning/caching and [LocalMapper] to convert internal models into SDK models.
 *
 * Id scheme:
 * - Song id  = the MediaStore `content://` URI string (directly playable, so [resolveStream] just
 *   returns it).
 * - Album id / Artist id = the MediaStore album/artist id as a string.
 */
class LocalSource(context: Context) : SourceProvider {

    private val mediaScanner = LocalMediaScanner(context)
    private val mapper = LocalMapper()

    /** Populate (or refresh) the MediaStore cache. Cheap once scanned thanks to the scanner's cache. */
    suspend fun ensureScanned() {
        mediaScanner.scanMedia()
    }

    override suspend fun search(request: SearchRequest): SearchResult {
        ensureScanned()
        val query = request.query
        val types = request.types
        val limit = request.page.limit

        val includeSongs = types.isEmpty() || MediaType.SONG in types
        val includeAlbums = types.isEmpty() || MediaType.ALBUM in types
        val includeArtists = types.isEmpty() || MediaType.ARTIST in types

        // When several types are requested at once, split the budget between them.
        val typeCount = listOf(includeSongs, includeAlbums, includeArtists).count { it }
        val perType = if (typeCount > 1) (limit / typeCount).coerceAtLeast(1) else limit

        val items = mutableListOf<MediaItem>()

        if (includeSongs) {
            mediaScanner.getAllSongs().filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true) ||
                    it.albumName.contains(query, ignoreCase = true)
            }.take(perType).forEach { items.add(mapper.toSong(it)) }
        }

        if (includeAlbums) {
            mediaScanner.getAllAlbums().filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true)
            }.take(perType).forEach { items.add(mapper.toAlbum(it)) }
        }

        if (includeArtists) {
            mediaScanner.getAllArtists().filter {
                it.name.contains(query, ignoreCase = true)
            }.take(perType).forEach { items.add(mapper.toArtist(it)) }
        }

        return SearchResult(items = items)
    }

    override suspend fun getSong(id: String): Song {
        ensureScanned()
        val song = mediaScanner.getAllSongs().find { it.contentUri.toString() == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Song not found: $id")
        return mapper.toSong(song)
    }

    override suspend fun getAlbum(id: String): Album {
        ensureScanned()
        val album = mediaScanner.getAllAlbums().find { it.id.toString() == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Album not found: $id")
        return mapper.toAlbum(album)
    }

    override suspend fun getArtist(id: String): Artist {
        ensureScanned()
        val artist = mediaScanner.getAllArtists().find { it.id.toString() == id }
            ?: throw PluginException(PluginErrorCode.NOT_FOUND, "Artist not found: $id")
        return mapper.toArtist(artist)
    }

    override suspend fun getPlaylist(id: String): Playlist {
        throw PluginException(PluginErrorCode.UNSUPPORTED, "No playlists")
    }

    override suspend fun getLibrarySongs(page: PageRequest): Page<Song> {
        ensureScanned()
        return paginate(mediaScanner.getAllSongs().map { mapper.toSong(it) }, page)
    }

    override suspend fun getLibraryAlbums(page: PageRequest): Page<Album> {
        ensureScanned()
        return paginate(mediaScanner.getAllAlbums().map { mapper.toAlbum(it) }, page)
    }

    override suspend fun getLibraryArtists(page: PageRequest): Page<Artist> {
        ensureScanned()
        return paginate(mediaScanner.getAllArtists().map { mapper.toArtist(it) }, page)
    }

    override suspend fun resolveStream(songId: String, type: MediaType): StreamResponse {
        // The song id is the MediaStore content:// URI, which the host can play directly.
        return StreamResponse.url(songId)
    }

    private fun <T> paginate(all: List<T>, page: PageRequest): Page<T> {
        val offset = page.cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val items = all.drop(offset).take(page.limit)
        val end = offset + items.size
        val nextCursor = if (end < all.size) end.toString() else null
        return Page(items = items, nextCursor = nextCursor, totalCount = all.size)
    }
}
