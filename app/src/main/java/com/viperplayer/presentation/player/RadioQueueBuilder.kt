package com.viperplayer.presentation.player

import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.Song

/**
 * Pure, Android-free construction of a "Song radio" queue from a [seed] track and its [related] songs.
 * The seed always comes first, followed by the related songs with the seed removed and duplicate ids
 * de-duplicated (first occurrence wins), preserving the plugin's related order. Delegates the id math
 * to [PlayerQueueLogic.radioQueueIds] so the ordering rule is single-sourced with the (still unit-
 * tested) queue logic; this hydrates that ordering back into full [Song]s. Co-located with
 * [PlayerQueueLogic] and kept pure so radio-queue generation is unit-testable independently of the
 * plugin fetch and the repository/Hilt wiring.
 */
object RadioQueueBuilder {

    /** The radio songs for [seed] + [related]: seed first, then related, deduplicated by [Song.id]. */
    fun buildSongs(seed: Song, related: List<Song>): List<Song> {
        val bySong: Map<MediaId, Song> = (listOf(seed) + related).associateBy { it.id }
        val orderedIds = PlayerQueueLogic.radioQueueIds(seed.id, related.map { it.id })
        return orderedIds.mapNotNull { bySong[it] }
    }
}
