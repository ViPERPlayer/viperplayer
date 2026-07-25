package com.viperplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.viperplayer.data.local.entity.GenreEntity
import com.viperplayer.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Genre operations.
 */
@Dao
interface GenreDao {

    @Query("SELECT * FROM genres WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): GenreEntity?

    @Query("SELECT * FROM genres WHERE id = :id")
    suspend fun getById(id: Long): GenreEntity?

    @Query("SELECT * FROM genres WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<GenreEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(genre: GenreEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(genres: List<GenreEntity>): List<Long>

    @Query("SELECT id FROM genres WHERE name = :name")
    suspend fun getIdByName(name: String): Long?

    /**
     * Reactive list of the genres that have at least one song, each with its song count, ordered by
     * name. Only non-empty genres are surfaced (an INNER JOIN over `song_genres`), so a genre whose
     * songs were all removed disappears from the Library. Re-emits whenever songs or their genre links
     * change.
     */
    @Query(
        """
        SELECT g.id AS id, g.name AS name, COUNT(sg.songId) AS songCount
        FROM genres g
        INNER JOIN song_genres sg ON sg.genreId = g.id
        GROUP BY g.id, g.name
        ORDER BY g.name COLLATE NOCASE ASC
        """
    )
    fun getGenresWithSongCounts(): Flow<List<GenreWithSongCount>>

    /** Reactive songs tagged with [genreId], ordered by title; re-emits on song/link changes. */
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN song_genres sg ON sg.songId = s.id
        WHERE sg.genreId = :genreId
        ORDER BY s.title COLLATE NOCASE ASC
        """
    )
    fun getSongsForGenre(genreId: Long): Flow<List<SongEntity>>
}

/** Projection of a genre plus how many songs are tagged with it, for the Library genre list. */
data class GenreWithSongCount(
    val id: Long,
    val name: String,
    val songCount: Int,
)

