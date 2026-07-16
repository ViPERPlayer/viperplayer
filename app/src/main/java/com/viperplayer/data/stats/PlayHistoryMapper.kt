package com.viperplayer.data.stats

import com.viperplayer.domain.stats.PlayRecord

/** Maps a stored [PlayHistoryEntity] to the pure [PlayRecord] the aggregator consumes. */
fun PlayHistoryEntity.toPlayRecord(): PlayRecord = PlayRecord(
    timestampMs = timestamp,
    mediaId = mediaId,
    title = title,
    artist = artist,
    album = album,
    pluginId = pluginId,
    listenedMs = listenedMs,
    durationMs = durationMs,
    artworkUrl = artworkUrl,
)
