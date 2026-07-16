package com.viperplayer.data.player.resumption

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Pure (de)serialization for [LastSession] and the pure "what to restore" selection logic.
 *
 * No Android dependencies — this is the JVM unit-test target. The wire format is versioned JSON so
 * a future schema change can be detected and old/malformed blobs degrade to `null` rather than
 * crashing resumption.
 */
object LastSessionCodec {

    /** Bump when the persisted schema changes incompatibly; older/newer versions decode to `null`. */
    const val VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class Wire(
        val version: Int = VERSION,
        val items: List<WireItem> = emptyList(),
        val currentIndex: Int = 0,
        val positionMs: Long = 0L,
        val playWhenReady: Boolean = false,
        val shuffleEnabled: Boolean = false,
        val repeatMode: String = "OFF",
    )

    @Serializable
    private data class WireItem(
        val pluginId: String,
        val sourceId: String,
        val title: String = "",
        val artist: String? = null,
        val album: String? = null,
        val artworkUrl: String? = null,
        val durationMs: Long? = null,
        val isVideo: Boolean = false,
    )

    /** Serializes a session to a stable JSON string. */
    fun encode(session: LastSession): String = json.encodeToString(
        Wire.serializer(),
        Wire(
            version = VERSION,
            items = session.items.map {
                WireItem(
                    pluginId = it.pluginId,
                    sourceId = it.sourceId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    durationMs = it.durationMs,
                    isVideo = it.isVideo,
                )
            },
            currentIndex = session.currentIndex,
            positionMs = session.positionMs,
            playWhenReady = session.playWhenReady,
            shuffleEnabled = session.shuffleEnabled,
            repeatMode = session.repeatMode,
        )
    )

    /**
     * Deserializes a session. Returns `null` — never throws — for `null`/blank input, malformed
     * JSON, a version mismatch, or a payload with no restorable items. Items missing a plugin/source
     * id are dropped; if that leaves nothing, the whole result is `null`.
     */
    fun decode(stored: String?): LastSession? {
        if (stored.isNullOrBlank()) return null
        val wire = try {
            json.decodeFromString(Wire.serializer(), stored)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (wire.version != VERSION) return null

        val items = wire.items
            .filter { it.pluginId.isNotBlank() && it.sourceId.isNotBlank() }
            .map {
                LastSessionItem(
                    pluginId = it.pluginId,
                    sourceId = it.sourceId,
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    durationMs = it.durationMs,
                    isVideo = it.isVideo,
                )
            }
        if (items.isEmpty()) return null

        return LastSession(
            items = items,
            currentIndex = wire.currentIndex,
            positionMs = wire.positionMs,
            playWhenReady = wire.playWhenReady,
            shuffleEnabled = wire.shuffleEnabled,
            repeatMode = wire.repeatMode,
        )
    }

    /**
     * Pure "what to restore" selection: clamps [LastSession.currentIndex] into the item range and
     * [LastSession.positionMs] to be non-negative (and to `0` when the index was out of range, since
     * a position only makes sense against a known item). Returns `null` when there is nothing to
     * restore. The result is exactly what a media3 `MediaItemsWithStartPosition` needs.
     */
    fun selectRestore(session: LastSession?): RestoreSelection? {
        if (session == null || session.items.isEmpty()) return null
        val lastIndex = session.items.lastIndex
        val rawIndex = session.currentIndex
        val index = rawIndex.coerceIn(0, lastIndex)
        val positionMs = if (rawIndex in 0..lastIndex) session.positionMs.coerceAtLeast(0L) else 0L
        return RestoreSelection(
            items = session.items,
            startIndex = index,
            startPositionMs = positionMs,
            playWhenReady = session.playWhenReady,
            shuffleEnabled = session.shuffleEnabled,
            repeatMode = session.repeatMode,
        )
    }
}

/** Pure result of [LastSessionCodec.selectRestore]: the items plus a valid start index/position. */
data class RestoreSelection(
    val items: List<LastSessionItem>,
    val startIndex: Int,
    val startPositionMs: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: String,
)
