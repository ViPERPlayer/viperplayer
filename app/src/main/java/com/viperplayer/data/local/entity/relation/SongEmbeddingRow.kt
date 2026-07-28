package com.viperplayer.data.local.entity.relation

import androidx.room.ColumnInfo

/**
 * Minimal (songId, embedding) projection over the `songs` table for building the local kNN index —
 * avoids loading full [com.viperplayer.data.local.entity.SongEntity] rows just to read the vector.
 * [embedding] is the 512-float32 little-endian BLOB (see `ClapAudioEncoder.bytesToEmbedding`).
 *
 * A [ByteArray] has no value-based equals/hashCode; this projection is a transient read model (never
 * a set/map key or persisted), so the default reference-identity data-class members are acceptable
 * and intentionally left as-is.
 */
data class SongEmbeddingRow(
    @ColumnInfo(name = "songId") val songId: Long,
    @ColumnInfo(name = "embedding") val embedding: ByteArray
)
