package com.viperplayer.follows.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowedArtistDao {

    /** All followed artists, newest first. Ordering for display is applied above this. */
    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC, id DESC")
    fun observeAll(): Flow<List<FollowedArtistEntity>>

    /** Live "is this artist followed?" flag for a single artist. */
    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE pluginId = :pluginId AND sourceId = :sourceId)")
    fun observeIsFollowing(pluginId: String, sourceId: String): Flow<Boolean>

    /** Live count of followed artists, for the Library "Following" shortcut tile. */
    @Query("SELECT COUNT(*) FROM followed_artists")
    fun observeCount(): Flow<Int>

    /**
     * Idempotent insert: the unique (pluginId, sourceId) index means a second follow of the same
     * artist is ignored rather than creating a duplicate row.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: FollowedArtistEntity): Long

    @Query("DELETE FROM followed_artists WHERE pluginId = :pluginId AND sourceId = :sourceId")
    suspend fun delete(pluginId: String, sourceId: String)
}
