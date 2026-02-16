package com.viperplayer.local

import android.content.Context
import android.util.Log
import com.viperplayer.local.data.LocalMediaScanner
import com.viperplayer.local.mapper.LocalMapper
import com.viperplayer.plugin.PagedResult
import com.viperplayer.plugin.ViperPlugin
import com.viperplayer.plugin.v1.Album
import com.viperplayer.plugin.v1.Artist
import com.viperplayer.plugin.v1.HomeContent
import com.viperplayer.plugin.v1.HomeSection
import com.viperplayer.plugin.v1.IHostCallbackV1
import com.viperplayer.plugin.v1.MediaItemV1
import com.viperplayer.plugin.v1.Playlist
import com.viperplayer.plugin.v1.PluginCapabilities
import com.viperplayer.plugin.v1.SearchFilter
import com.viperplayer.plugin.v1.SearchResult
import com.viperplayer.plugin.v1.SearchSuggestionsResultV1
import com.viperplayer.plugin.v1.Song
import com.viperplayer.plugin.v1.StreamSource

private const val TAG = "LocalPlugin"

class LocalPlugin(
    private val context: Context
) : ViperPlugin {

    private val mediaScanner = LocalMediaScanner(context)
    private val mapper = LocalMapper()

    override val capabilities: PluginCapabilities
        get() = PluginCapabilities(
            canSearch = true,
            hasLibrary = true,
            hasPlaylists = false,
            supportsOffline = true
        )

    override suspend fun onConnect(hostCallback: IHostCallbackV1) {
        Log.d(TAG, "LocalPlugin connected")
        // Trigger initial scan on connection
        mediaScanner.scanMedia()
    }

    override suspend fun onDisconnect() {
        Log.d(TAG, "LocalPlugin disconnected")
    }

    override suspend fun getSearchSuggestions(query: String): SearchSuggestionsResultV1 {
        val allSongs = mediaScanner.getAllSongs()
        val allAlbums = mediaScanner.getAllAlbums()
        val allArtists = mediaScanner.getAllArtists()

        // Collect unique suggestions from song titles, album names, and artist names
        val suggestions = mutableSetOf<String>()
        
        allSongs.forEach { song ->
            if (song.title.contains(query, ignoreCase = true)) {
                suggestions.add(song.title)
            }
        }
        
        allAlbums.forEach { album ->
            if (album.name.contains(query, ignoreCase = true)) {
                suggestions.add(album.name)
            }
        }
        
        allArtists.forEach { artist ->
            if (artist.name.contains(query, ignoreCase = true)) {
                suggestions.add(artist.name)
            }
        }

        // Get direct hits - matching items
        val items = mutableListOf<MediaItemV1>()
        
        allSongs.filter { it.title.contains(query, ignoreCase = true) }
            .take(3)
            .forEach { items.add(MediaItemV1.song(mapper.toSong(it))) }
            
        allAlbums.filter { it.name.contains(query, ignoreCase = true) }
            .take(2)
            .forEach { items.add(MediaItemV1.album(mapper.toAlbum(it))) }
            
        allArtists.filter { it.name.contains(query, ignoreCase = true) }
            .take(2)
            .forEach { items.add(MediaItemV1.artist(mapper.toArtist(it))) }

        return SearchSuggestionsResultV1(
            suggestions = suggestions.take(10),
            items = items
        )
    }

    override suspend fun search(
        query: String,
        filter: SearchFilter?,
        cursor: String?,
        limit: Int
    ): SearchResult {
        val allSongs = mediaScanner.getAllSongs()
        val allAlbums = mediaScanner.getAllAlbums()
        val allArtists = mediaScanner.getAllArtists()

        val items = mutableListOf<MediaItemV1>()

        when (filter) {
            SearchFilter.SONG -> {
                allSongs.filter { 
                    it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true) ||
                    it.albumName.contains(query, ignoreCase = true)
                }.take(limit).forEach {
                    items.add(MediaItemV1.song(mapper.toSong(it)))
                }
            }
            SearchFilter.ALBUM -> {
                allAlbums.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true)
                }.take(limit).forEach {
                    items.add(MediaItemV1.album(mapper.toAlbum(it)))
                }
            }
            SearchFilter.ARTIST -> {
                allArtists.filter {
                    it.name.contains(query, ignoreCase = true)
                }.take(limit).forEach {
                    items.add(MediaItemV1.artist(mapper.toArtist(it)))
                }
            }
            null -> {
                // Search all types
                allSongs.filter { 
                    it.title.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true)
                }.take(limit / 3).forEach {
                    items.add(MediaItemV1.song(mapper.toSong(it)))
                }
                
                allAlbums.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.artistName.contains(query, ignoreCase = true)
                }.take(limit / 3).forEach {
                    items.add(MediaItemV1.album(mapper.toAlbum(it)))
                }
                
                allArtists.filter {
                    it.name.contains(query, ignoreCase = true)
                }.take(limit / 3).forEach {
                    items.add(MediaItemV1.artist(mapper.toArtist(it)))
                }
            }
            else -> {
                // Unsupported filter type, return empty
            }
        }

        return SearchResult(items = items)
    }

    override suspend fun getSong(id: String): Song {
        val allSongs = mediaScanner.getAllSongs()
        val song = allSongs.find { "local:${it.contentUri}" == id }
            ?: throw IllegalArgumentException("Song not found: $id")
        return mapper.toSong(song)
    }

    override suspend fun getAlbum(id: String): Album {
        val allAlbums = mediaScanner.getAllAlbums()
        val album = allAlbums.find { "local:album:${it.id}" == id }
            ?: throw IllegalArgumentException("Album not found: $id")
        return mapper.toAlbum(album)
    }

    override suspend fun getArtist(id: String): Artist {
        val allArtists = mediaScanner.getAllArtists()
        val artist = allArtists.find { "local:artist:${it.id}" == id }
            ?: throw IllegalArgumentException("Artist not found: $id")
        return mapper.toArtist(artist)
    }

    override suspend fun getPlaylist(mediaId: String): Playlist {
        // Local plugin doesn't support playlists
        throw UnsupportedOperationException("Local plugin does not support playlists")
    }

    override suspend fun getLibrarySongs(cursor: String?, limit: Int): PagedResult<Song> {
        val allSongs = mediaScanner.getAllSongs()
        val songs = allSongs.map { mapper.toSong(it) }
        return PagedResult(
            items = songs.take(limit),
            nextCursor = null // No pagination for now
        )
    }

    override suspend fun getHomeSections(): HomeContent {
        val allSongs = mediaScanner.getAllSongs()
        val allAlbums = mediaScanner.getAllAlbums()
        val allArtists = mediaScanner.getAllArtists()

        val sections = mutableListOf<HomeSection>()

        // Recent songs section
        if (allSongs.isNotEmpty()) {
            sections.add(
                HomeSection(
                    id = "recent_songs",
                    title = "Recent Songs",
                    items = allSongs.take(20).map { MediaItemV1.song(mapper.toSong(it)) }
                )
            )
        }

        // Albums section
        if (allAlbums.isNotEmpty()) {
            sections.add(
                HomeSection(
                    id = "albums",
                    title = "Albums",
                    items = allAlbums.take(20).map { MediaItemV1.album(mapper.toAlbum(it)) }
                )
            )
        }

        // Artists section
        if (allArtists.isNotEmpty()) {
            sections.add(
                HomeSection(
                    id = "artists",
                    title = "Artists",
                    items = allArtists.take(20).map { MediaItemV1.artist(mapper.toArtist(it)) }
                )
            )
        }

        return HomeContent(
            sections = sections,
            quickPicks = null
        )
    }

    override suspend fun getStream(mediaId: String): StreamSource {
        // Extract content URI from the mediaId
        val contentUri = mediaId.removePrefix("local:")
        Log.d(TAG, "Getting stream for: $contentUri")
        
        // Return StreamSource with the content:// URI
        // ExoPlayer can directly play content:// URIs
        return StreamSource.url(contentUri)
    }
}
