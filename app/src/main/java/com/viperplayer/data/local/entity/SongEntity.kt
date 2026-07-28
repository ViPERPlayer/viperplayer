package com.viperplayer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Song.
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["idType", "pluginId", "sourceId"], unique = true),
        Index(value = ["albumId"]),
        Index(value = ["isLiked"]),
        Index(value = ["isSaved"]),
        Index(value = ["isDownloaded"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // MediaId identity, type-discriminated: idType is "plugin"|"local"; pluginId is "" for local.
    val idType: String,
    val pluginId: String,
    val sourceId: String,
    val title: String,
    val albumId: Long? = null, // Reference to album
    val durationMs: Long? = 0,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isExplicit: Boolean = false,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isDownloaded: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isVideo: Boolean = false,
    val downloadPath: String? = null,
    val localArtworkPath: String? = null, // Local path to cached artwork
    // Audio normalization
    val replayGainDb: Float? = null, // ReplayGain value in dB
    val peakAmplitude: Float? = null, // Peak amplitude (0.0-1.0+)
    val playCount: Long = 0,
    val lastPlayed: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    // On-device CLAP audio embedding (recommender, Room v6). All three are NULL until an embedding is
    // computed for this song. [audioEmbedding] is the 512-float32 little-endian vector = 2048 bytes
    // (BLOB), stamped with the [embeddingModelVersion] (ClapModel.MODEL_VERSION) it was produced with
    // and the wall-clock ms it was computed at. A row whose version differs from the current
    // ClapModel.MODEL_VERSION is stale and re-computed by the indexer (later wave).
    val audioEmbedding: ByteArray? = null,
    val embeddingModelVersion: String? = null,
    val embeddingComputedAtMs: Long? = null,
) {
    // A ByteArray does not have value-based equals/hashCode, so the compiler-generated data-class
    // members would compare embeddings by reference and break equality/dedup for identical rows.
    // Override to compare the embedding by content (all other fields are already value types).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SongEntity) return false
        if (id != other.id) return false
        if (idType != other.idType) return false
        if (pluginId != other.pluginId) return false
        if (sourceId != other.sourceId) return false
        if (title != other.title) return false
        if (albumId != other.albumId) return false
        if (durationMs != other.durationMs) return false
        if (artworkUrl != other.artworkUrl) return false
        if (trackNumber != other.trackNumber) return false
        if (discNumber != other.discNumber) return false
        if (isExplicit != other.isExplicit) return false
        if (isLiked != other.isLiked) return false
        if (isSaved != other.isSaved) return false
        if (isDownloaded != other.isDownloaded) return false
        if (isVideo != other.isVideo) return false
        if (downloadPath != other.downloadPath) return false
        if (localArtworkPath != other.localArtworkPath) return false
        if (replayGainDb != other.replayGainDb) return false
        if (peakAmplitude != other.peakAmplitude) return false
        if (playCount != other.playCount) return false
        if (lastPlayed != other.lastPlayed) return false
        if (lastUpdated != other.lastUpdated) return false
        if (!audioEmbedding.contentEquals(other.audioEmbedding)) return false
        if (embeddingModelVersion != other.embeddingModelVersion) return false
        if (embeddingComputedAtMs != other.embeddingComputedAtMs) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + idType.hashCode()
        result = 31 * result + pluginId.hashCode()
        result = 31 * result + sourceId.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (albumId?.hashCode() ?: 0)
        result = 31 * result + (durationMs?.hashCode() ?: 0)
        result = 31 * result + (artworkUrl?.hashCode() ?: 0)
        result = 31 * result + (trackNumber ?: 0)
        result = 31 * result + (discNumber ?: 0)
        result = 31 * result + isExplicit.hashCode()
        result = 31 * result + isLiked.hashCode()
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + isDownloaded.hashCode()
        result = 31 * result + isVideo.hashCode()
        result = 31 * result + (downloadPath?.hashCode() ?: 0)
        result = 31 * result + (localArtworkPath?.hashCode() ?: 0)
        result = 31 * result + (replayGainDb?.hashCode() ?: 0)
        result = 31 * result + (peakAmplitude?.hashCode() ?: 0)
        result = 31 * result + playCount.hashCode()
        result = 31 * result + (lastPlayed?.hashCode() ?: 0)
        result = 31 * result + lastUpdated.hashCode()
        result = 31 * result + (audioEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + (embeddingModelVersion?.hashCode() ?: 0)
        result = 31 * result + (embeddingComputedAtMs?.hashCode() ?: 0)
        return result
    }
}

