package com.viperplayer.data.player.resumption

/**
 * A snapshot of the last meaningful playback session, persisted so the system can show a media
 * resumption card (Android 11+) and rebuild the queue after a reboot / app sweep.
 *
 * This is a **pure** model with no Android dependencies. Serialization lives in [LastSessionCodec]
 * and is the JVM unit-test target. Enough per-item metadata (title/artist/album/artwork) is kept
 * inline so the resumption card and the restored [androidx.media3.common.MediaItem]s can be built
 * without touching the database or the network — the actual stream URI is resolved lazily on play.
 */
data class LastSession(
    val items: List<LastSessionItem>,
    /** Index of the current item within [items]. Clamped into range by [selectRestore]. */
    val currentIndex: Int,
    /** Playback position within the current item, in milliseconds. */
    val positionMs: Long,
    /** Whether playback was ongoing (play-when-ready) at save time. */
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean,
    /** Repeat mode name, matching [com.viperplayer.domain.model.RepeatMode]. */
    val repeatMode: String,
)

/**
 * A single restorable queue entry. [pluginId]/[sourceId] together form the domain `MediaId`; the
 * remaining fields are cached display metadata so the card and restored item can be rendered
 * offline.
 */
data class LastSessionItem(
    val pluginId: String,
    val sourceId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val isVideo: Boolean,
)
