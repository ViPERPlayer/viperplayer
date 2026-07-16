package com.viperplayer.follows.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Dedicated Room database for the follows (subscriptions) feature. Deliberately separate from the
 * app's shared [com.viperplayer.data.local.ViperPlayerDatabase] so the feature stays self-contained
 * and doesn't force a version bump / destructive wipe of the main library DB.
 */
@Database(entities = [FollowedArtistEntity::class], version = 1, exportSchema = false)
abstract class FollowedArtistsDatabase : RoomDatabase() {
    abstract fun followedArtistDao(): FollowedArtistDao
}
