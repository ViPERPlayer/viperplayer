package com.viperplayer.data.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import com.viperplayer.data.player.cast.CastSessionCommands
import androidx.core.net.toUri
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.viperplayer.R
import com.viperplayer.data.player.MediaItemMapper.toMediaItem
import com.viperplayer.data.player.resumption.LastSessionCodec
import com.viperplayer.data.player.resumption.LastSessionMediaMapper
import com.viperplayer.data.player.resumption.LastSessionStore
import com.viperplayer.data.resources.StringProvider
import com.viperplayer.domain.model.Album
import com.viperplayer.domain.model.Artist
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.PlayerRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.usecase.search.SearchUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import timber.log.Timber
import javax.inject.Inject

class ViperMediaLibrarySessionCallback @Inject constructor(
    private val mediaLibraryRepository: MediaLibraryRepository,
    private val searchUseCase: SearchUseCase,
    private val playerRepository: PlayerRepository,
    private val pluginRepository: PluginRepository,
    private val lastSessionStore: LastSessionStore,
    private val stringProvider: StringProvider,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val ROOT_ID = "root"
        private const val ARTISTS_ID = "artists"
        private const val ALBUMS_ID = "albums"
        private const val PLAYLISTS_ID = "playlists"
        private const val SONGS_ID = "songs"

        private const val PREFIX_ARTIST = "artist/"
        private const val PREFIX_ALBUM = "album/"
        private const val PREFIX_PLAYLIST = "playlist/"
    }

    /**
     * Grant every connecting controller the custom casting-state command on top of the default set,
     * so the service can [MediaSession.broadcastCustomCommand] the "casting changed" signal and the
     * app's [androidx.media3.session.MediaController] receives it (the standard Player interface has
     * no cast signal). Media playback commands are left at their defaults.
     */
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val default = super.onConnect(session, controller)
        val sessionCommands = default.availableSessionCommands.buildUpon()
            .add(SessionCommand(CastSessionCommands.ACTION_CASTING_CHANGED, Bundle.EMPTY))
            .build()
        return MediaSession.ConnectionResult.accept(sessionCommands, default.availablePlayerCommands)
    }

    /**
     * Media resumption entry point (Android 11+ "resume" media controls). After a reboot or an app
     * sweep the framework calls this to rebuild the last session; the returned
     * [MediaItemsWithStartPosition] is used to prepare the player **paused** (the framework does not
     * auto-start playback here). We load the persisted [com.viperplayer.data.player.resumption.LastSession],
     * run the pure clamp/selection in [LastSessionCodec.selectRestore], rebuild playable
     * [MediaItem]s (empty URI → stream re-resolved lazily on play, so expired URIs recover for free),
     * and restore shuffle/repeat on the session player. Degrades to an empty result when there is
     * nothing (valid) to restore, which the framework treats as "no resumable content".
     */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaItemsWithStartPosition> {
        return serviceScope.future {
            // No (valid) last session: fail the future so the framework treats it as "nothing to
            // resume" rather than applying an empty queue.
            val selection = LastSessionCodec.selectRestore(lastSessionStore.load())
                ?: throw IllegalStateException("No resumable session")

            val mediaItems = selection.items.map { LastSessionMediaMapper.toMediaItem(it) }

            // Restore shuffle/repeat on the session player. play-when-ready is intentionally left to
            // the framework, which prepares paused per platform guidance.
            runCatching {
                mediaSession.player.shuffleModeEnabled = selection.shuffleEnabled
                mediaSession.player.repeatMode = when (selection.repeatMode) {
                    RepeatMode.ONE.name -> Player.REPEAT_MODE_ONE
                    RepeatMode.ALL.name -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }.onFailure { Timber.w(it, "Failed to restore shuffle/repeat during resumption") }

            Timber.d("onPlaybackResumption: restoring ${mediaItems.size} items at index=${selection.startIndex}, pos=${selection.startPositionMs}ms")
            MediaItemsWithStartPosition(mediaItems, selection.startIndex, selection.startPositionMs)
        }
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setTitle(stringProvider.getString(R.string.nav_library))
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                val items = when {
                    parentId == ROOT_ID -> getRootChildren()
                    parentId == ARTISTS_ID -> getAllArtists()
                    parentId == ALBUMS_ID -> getAllAlbums()
                    parentId == PLAYLISTS_ID -> getAllPlaylists()
                    parentId == SONGS_ID -> getAllSongs()
                    parentId.startsWith(PREFIX_ARTIST) -> getArtistChildren(
                        parentId.removePrefix(
                            PREFIX_ARTIST
                        )
                    )

                    parentId.startsWith(PREFIX_ALBUM) -> getAlbumChildren(
                        parentId.removePrefix(
                            PREFIX_ALBUM
                        )
                    )

                    parentId.startsWith(PREFIX_PLAYLIST) -> getPlaylistChildren(
                        parentId.removePrefix(
                            PREFIX_PLAYLIST
                        )
                    )

                    else -> emptyList()
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            } catch (e: Exception) {
                Timber.e(e, "Error in onGetChildren for parentId: $parentId")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return serviceScope.future {
            try {
                // Try to resolve as Song first
                val songId = try {
                    MediaId.decode(mediaId)
                } catch (e: Exception) {
                    null
                }
                if (songId != null) {
                    val song = mediaLibraryRepository.getSong(songId).first()
                        ?: pluginRepository.getSong(songId).getOrNull()

                    if (song != null) {
                        return@future LibraryResult.ofItem(song.toMediaItem(), null)
                    }
                }

                // Fallback or other types if needed (usually onGetItem used for playable items)
                LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            } catch (e: Exception) {
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return serviceScope.future {
            // Initiate search but don't return results here as per API spec
            // Results are retrieved via onGetSearchResult
            session.notifySearchResultChanged(browser, query, 0, params)
            LibraryResult.ofVoid(params)
        }
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                val result = searchUseCase(query)
                val searchResult = result.getOrNull()

                if (searchResult != null) {
                    val mediaItems = searchResult.items.mapNotNull {
                        when (it) {
                            is Song -> it.toMediaItem()
                            is Artist -> buildBrowsableItem(
                                PREFIX_ARTIST + it.id.encode(),
                                it.name,
                                it.imageUrl,
                                MediaMetadata.MEDIA_TYPE_ARTIST
                            )

                            is Album -> buildBrowsableItem(
                                PREFIX_ALBUM + it.id.encode(),
                                it.name,
                                it.artworkUrl,
                                MediaMetadata.MEDIA_TYPE_ALBUM
                            )

                            is Playlist -> buildBrowsableItem(
                                PREFIX_PLAYLIST + it.id.encode(),
                                it.name,
                                it.artworkUrl,
                                MediaMetadata.MEDIA_TYPE_PLAYLIST
                            )

                            else -> null
                        }
                    }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                } else {
                    LibraryResult.ofItemList(ImmutableList.of(), params)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in onGetSearchResult for query: $query")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        return serviceScope.future {
            val resolvedMediaItems = mediaItems.mapNotNull { mediaItem ->
                try {
                    val mediaId = MediaId.decode(mediaItem.mediaId) ?: return@mapNotNull mediaItem
                    val song = mediaLibraryRepository.getSong(mediaId).first()
                        ?: pluginRepository.getSong(mediaId).getOrNull()

                    song?.toMediaItem() ?: mediaItem
                } catch (e: Exception) {
                    // If parsing fails or fetch fails, pass original item 
                    // (though it likely won't play if ID was invalid)
                    mediaItem
                }
            }.toMutableList()

            resolvedMediaItems
        }
    }

    private fun getRootChildren(): List<MediaItem> {
        return listOf(
            buildBrowsableItem(ARTISTS_ID, stringProvider.getString(R.string.library_tab_artists), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS),
            buildBrowsableItem(ALBUMS_ID, stringProvider.getString(R.string.library_tab_albums), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
            buildBrowsableItem(PLAYLISTS_ID, stringProvider.getString(R.string.library_tab_playlists), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
            buildBrowsableItem(SONGS_ID, stringProvider.getString(R.string.library_tab_songs), mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        )
    }

    private suspend fun getAllArtists(): List<MediaItem> {
        val artists = mediaLibraryRepository.getAllSavedArtists().first()
        return artists.map { artist ->
            buildBrowsableItem(
                PREFIX_ARTIST + artist.id.encode(), artist.name, artist.imageUrl,
                MediaMetadata.MEDIA_TYPE_ARTIST
            )
        }
    }

    private suspend fun getAllAlbums(): List<MediaItem> {
        val albums = mediaLibraryRepository.getAllSavedAlbums().first()
        return albums.map { album ->
            buildBrowsableItem(
                PREFIX_ALBUM + album.id.encode(), album.name, album.artworkUrl,
                MediaMetadata.MEDIA_TYPE_ALBUM
            )
        }
    }

    private suspend fun getAllPlaylists(): List<MediaItem> {
        val playlists = mediaLibraryRepository.getAllSavedPlaylists().first()
        return playlists.map { playlist ->
            buildBrowsableItem(
                PREFIX_PLAYLIST + playlist.id.encode(),
                playlist.name,
                playlist.artworkUrl,
                MediaMetadata.MEDIA_TYPE_PLAYLIST
            )
        }
    }

    private suspend fun getAllSongs(): List<MediaItem> {
        val songs = mediaLibraryRepository.getAllSavedSongs().first()
        return songs.map { it.toMediaItem() }
    }

    private suspend fun getArtistChildren(artistIdString: String): List<MediaItem> {
        val artistId = MediaId.decode(artistIdString) ?: return emptyList()
        // Try fetching from plugin repository first (remote)
        val artist = pluginRepository.getArtist(artistId).getOrNull()
        if (artist != null && artist.topSongs.isNotEmpty()) {
            return artist.topSongs.map { it.toMediaItem() }
        }

        // Fallback to local DB
        val allSongs = mediaLibraryRepository.getAllSavedSongs().first()
        return allSongs.filter { song ->
            song.artists.any { it.id == artistId }
        }.map { it.toMediaItem() }
    }

    private suspend fun getAlbumChildren(albumIdString: String): List<MediaItem> {
        val albumId = MediaId.decode(albumIdString) ?: return emptyList()
        val album = pluginRepository.getAlbum(albumId).getOrNull()
        if (album?.songs != null && album.songs.isNotEmpty()) {
            return album.songs.map { it.toMediaItem() }
        }

        val allSongs = mediaLibraryRepository.getAllSavedSongs().first()
        return allSongs.filter { song ->
            song.album?.id == albumId
        }.map { it.toMediaItem() }
    }

    private suspend fun getPlaylistChildren(playlistIdString: String): List<MediaItem> {
        val playlistId = MediaId.decode(playlistIdString) ?: return emptyList()
        val playlist = pluginRepository.getPlaylist(playlistId).getOrNull()
        if (playlist?.songs != null && playlist.songs.isNotEmpty()) {
            return playlist.songs.map { it.toMediaItem() }
        }

        val localPlaylist = mediaLibraryRepository.getPlaylist(playlistId).first()
        return localPlaylist?.songs?.map { it.toMediaItem() } ?: emptyList()
    }

    private fun buildBrowsableItem(
        id: String,
        title: String,
        iconUri: String? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(mediaType)

        // Artwork so Android Auto / the media browser render a thumbnail rather than a plain row.
        if (!iconUri.isNullOrBlank()) {
            metadata.setArtworkUri(iconUri.toUri())
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata.build())
            .build()
    }
}
