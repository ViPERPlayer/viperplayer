package com.viperplayer.data.repository

import android.content.Context
import com.viperplayer.R
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Playlist
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.radio.RadioPlaylist
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.RadioPlaylistRepository
import com.viperplayer.domain.player.RadioQueueBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Live implementation of [RadioPlaylistRepository]. Resolves the seed song, fetches the plugin's
 * related songs (the exact source the old `startSongRadio` used) and delegates the ordering to the pure
 * [RadioQueueBuilder]. No queue-building logic lives here — this only gathers the inputs and wraps the
 * resulting song list in a "Song radio" [Playlist] header carrying the radio's synthetic id.
 */
class RadioPlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginRepository: PluginRepository,
) : RadioPlaylistRepository {

    override suspend fun getRadioPlaylist(seedId: MediaId): Playlist {
        val seed = pluginRepository.getSong(seedId).getOrNull()
            ?: return header(seedId, songs = emptyList(), artworkUrl = null)
        val related = pluginRepository.getRelatedSongs(seedId).getOrNull()?.items.orEmpty()
        val songs = RadioQueueBuilder.buildSongs(seed, related)
        return header(seedId, songs = songs, artworkUrl = seed.artworkUrl)
    }

    /** Builds the virtual radio [Playlist] header carrying the radio's synthetic id and its songs. */
    private fun header(seedId: MediaId, songs: List<Song>, artworkUrl: String?): Playlist =
        Playlist(
            id = RadioPlaylist.buildMediaId(seedId),
            name = context.getString(R.string.action_song_radio),
            artworkUrl = artworkUrl,
            ownerName = null,
            songCount = songs.size,
            isPublic = false,
            isEditable = false,
            songs = songs,
        )
}
