package com.viperplayer.data.repository

import com.viperplayer.data.local.dao.LyricsPrefsDao
import com.viperplayer.data.local.entity.LyricsPrefsEntity
import com.viperplayer.data.lyrics.LyricsJson
import com.viperplayer.domain.model.Lyrics
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.repository.LyricsPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [LyricsPreferencesRepository]. One [LyricsPrefsEntity] row per customized song (keyed
 * by [MediaId.encode]) carries both the timing offset and the serialized manual override; the DAO
 * prunes a row that decays back to all defaults. The override [Lyrics] is (de)serialized via
 * [LyricsJson] since the domain model isn't itself `@Serializable`.
 */
@Singleton
class LyricsPreferencesRepositoryImpl @Inject constructor(
    private val dao: LyricsPrefsDao,
) : LyricsPreferencesRepository {

    override fun offset(mediaId: MediaId): Flow<Long> =
        dao.observe(mediaId.encode()).map { it?.offsetMs ?: 0L }

    override suspend fun setOffset(mediaId: MediaId, offsetMs: Long) {
        dao.setOffset(mediaId.encode(), offsetMs)
    }

    override fun override(mediaId: MediaId): Flow<Lyrics?> =
        dao.observe(mediaId.encode()).map { entity ->
            entity?.overrideJson?.let { json ->
                runCatching { LyricsJson.decode(json) }
                    .onFailure { Timber.w(it, "Failed to decode lyrics override") }
                    .getOrNull()
            }
        }

    override suspend fun setOverride(mediaId: MediaId, lyrics: Lyrics) {
        dao.setOverride(mediaId.encode(), LyricsJson.encode(lyrics))
    }

    override suspend fun clearOverride(mediaId: MediaId) {
        dao.setOverride(mediaId.encode(), null)
    }
}
