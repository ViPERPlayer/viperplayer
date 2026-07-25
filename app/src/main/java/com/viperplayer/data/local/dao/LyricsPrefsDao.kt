package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.viperplayer.data.local.entity.LyricsPrefsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for per-song lyrics preferences ([LyricsPrefsEntity]) — the timing offset and the manual
 * lyric-match override, keyed by the song's `MediaId.encode()`.
 *
 * The setters read-modify-write the (rare, single-row) record so the offset and the override can be
 * changed independently without clobbering the other, and prune a row that has fallen back to all
 * defaults so the table only holds songs the user has actually customized.
 */
@Dao
interface LyricsPrefsDao {

    @Query("SELECT * FROM lyrics_prefs WHERE mediaId = :mediaId")
    fun observe(mediaId: String): Flow<LyricsPrefsEntity?>

    @Query("SELECT * FROM lyrics_prefs WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): LyricsPrefsEntity?

    @Upsert
    suspend fun upsert(entity: LyricsPrefsEntity)

    @Query("DELETE FROM lyrics_prefs WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    /** Set (or reset, when [offsetMs] is 0) the timing offset, preserving any override. */
    @Transaction
    suspend fun setOffset(mediaId: String, offsetMs: Long) {
        val existing = get(mediaId)
        val next = (existing ?: LyricsPrefsEntity(mediaId)).copy(offsetMs = offsetMs)
        writeOrPrune(next)
    }

    /** Set (or clear, when [overrideJson] is null) the manual override, preserving any offset. */
    @Transaction
    suspend fun setOverride(mediaId: String, overrideJson: String?) {
        val existing = get(mediaId)
        val next = (existing ?: LyricsPrefsEntity(mediaId)).copy(overrideJson = overrideJson)
        writeOrPrune(next)
    }

    /** Persist [entity], or delete its row when it has decayed to all defaults (offset 0, no override). */
    @Transaction
    suspend fun writeOrPrune(entity: LyricsPrefsEntity) {
        if (entity.offsetMs == 0L && entity.overrideJson == null) {
            delete(entity.mediaId)
        } else {
            upsert(entity)
        }
    }
}
