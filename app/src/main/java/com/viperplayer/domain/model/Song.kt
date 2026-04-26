package com.viperplayer.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents a song/track.
 */
@Serializable
@Parcelize
data class Song(
    override val id: MediaId,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long? = 0,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isExplicit: Boolean = false,
    val isPlayable: Boolean = true,
    val requiresInternet: Boolean = true, // Default to true for streaming services
    val replayGainDb: Float? = null, // Optional ReplayGain value in dB (converted to linear when applied to player)
    val peakAmplitude: Float? = null, // Peak amplitude (0.0-1.0+)
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
) : MediaItem, Parcelable {
    val artistNames: String?
        get() = artists.joinToString { it.name }.takeIf { it.isNotEmpty() }
}

