package com.viperplayer.data.rec

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A BOUNDED on-disk "save for later" cache of computed **log-mel** front-end tensors for streaming
 * songs captured (Path A) while the CLAP model was NOT yet installed. This realizes the user's
 * requirement to "save whatever we need for later processing" so a track played before the model
 * finished downloading is not lost: when the model becomes Ready, a drain step embeds the cached mels
 * and deletes them (see [StreamingIndexWorker]).
 *
 * ## Why store the mel, not the PCM
 * The mel is the compact, model-input-ready representation (1001*64 floats ≈ 256 KB) and is FIXED-SIZE
 * regardless of the source track. Persisting it means the drain only has to run the ONNX tower — no
 * re-decode, no resample. It is stamped with a [captureFormatVersion] so a change to the mel contract
 * invalidates old files (they are dropped, since they can no longer be embedded compatibly).
 *
 * ## Bounding / eviction
 * Files live under `filesDir/rec-captures/`. On each [put] the store evicts the OLDEST entries (by file
 * mod_time) until it is within [maxEntries] and [maxBytes]. This caps disk use even if the model never
 * installs. Android-coupled (needs `filesDir`), so it lives in the data layer; the pure round-trip +
 * eviction logic is unit-tested against a temp dir via [forDirectory].
 */
@Singleton
class PendingCaptureStore private constructor(
    private val dir: File,
    private val captureFormatVersion: Int,
    private val maxEntries: Int,
    private val maxBytes: Long,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        dir = File(context.filesDir, DIR_NAME),
        captureFormatVersion = CAPTURE_FORMAT_VERSION,
        maxEntries = DEFAULT_MAX_ENTRIES,
        maxBytes = DEFAULT_MAX_BYTES,
    )

    /** One cached capture: the encoded [mediaId] and its stored log-mel front-end buffer. */
    data class PendingCapture(val mediaId: String, val mel: FloatArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is PendingCapture) return false
            return mediaId == other.mediaId && mel.contentEquals(other.mel)
        }

        override fun hashCode(): Int = 31 * mediaId.hashCode() + mel.contentHashCode()
    }

    /**
     * Persist [mel] for [mediaId], replacing any prior entry for the same song, then evict oldest until
     * within bounds. Swallows/logs IO errors (a failed cache write must never crash playback). The mel
     * length is not validated here beyond being non-empty; the drain re-checks against the model.
     */
    @Synchronized
    fun put(mediaId: String, mel: FloatArray) {
        if (mel.isEmpty()) return
        runCatching {
            dir.mkdirs()
            val file = File(dir, fileName(mediaId))
            file.writeBytes(encodeWith(mediaId.toByteArray(Charsets.UTF_8), mel))
            // Never evict the entry we just wrote (it would be silently lost). Relevant when several
            // captures share the same coarse file mod-time and the sort tie-breaks arbitrarily.
            evictToBounds(keep = file.name)
        }.onFailure { Timber.w(it, "PendingCaptureStore: failed to cache capture for $mediaId") }
    }

    /** All currently-cached captures for the CURRENT [captureFormatVersion] (stale-version files skipped). */
    @Synchronized
    fun readAll(): List<PendingCapture> {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { decode(file.readBytes())?.let { PendingCapture(it.first, it.second) } }
                .onFailure { Timber.w(it, "PendingCaptureStore: dropping unreadable ${file.name}") }
                .getOrNull()
        }
    }

    /** Delete the cached capture for [mediaId] (called after it is embedded). No-op if absent. */
    @Synchronized
    fun remove(mediaId: String) {
        runCatching { File(dir, fileName(mediaId)).delete() }
    }

    /** Number of cached entries (test/observability helper). */
    @Synchronized
    fun size(): Int = dir.listFiles()?.count { it.isFile && it.name.endsWith(SUFFIX) } ?: 0

    /** Delete every cached capture (e.g. recommendations turned off / model version bumped). */
    @Synchronized
    fun clear() {
        dir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    // ---- internals ----

    /**
     * Evict oldest files (by lastModified) until within [maxEntries] and [maxBytes]. The [keep] entry (the
     * one just written by [put]) is NEVER a victim — it still counts toward the caps, but on a mod-time tie
     * (coarse FS granularity) the arbitrary sort order must not delete the freshest capture.
     */
    private fun evictToBounds(keep: String? = null) {
        val all = dir.listFiles()?.filter { it.isFile && it.name.endsWith(SUFFIX) }
            ?.sortedBy { it.lastModified() } // oldest first
            ?: return
        val victims = all.filter { it.name != keep }
        var remaining = all.size
        var totalBytes = all.sumOf { it.length() }
        var idx = 0
        while (idx < victims.size && (remaining > maxEntries || totalBytes > maxBytes)) {
            val victim = victims[idx]
            totalBytes -= victim.length()
            remaining -= 1
            victim.delete()
            idx++
        }
    }

    /** On-disk name for [mediaId]: a filesystem-safe base64url of the encoded id + version + suffix. */
    private fun fileName(mediaId: String): String {
        val key = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mediaId.toByteArray(Charsets.UTF_8))
        return "v${captureFormatVersion}_$key$SUFFIX"
    }

    /**
     * Serialize a capture: `[magic:int][version:int][mediaIdLen:int][mediaIdUtf8][melLen:int][mel:float32
     * LE...]`. Little-endian. Self-describing (mediaId travels inline as well as in the filename) so a
     * decoded file is self-contained and [decode] can validate the version + magic.
     */
    private fun encodeWith(mediaIdBytes: ByteArray, mel: FloatArray): ByteArray {
        val buf = ByteBuffer
            .allocate(HEADER_INTS * 4 + mediaIdBytes.size + mel.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC)
        buf.putInt(captureFormatVersion)
        buf.putInt(mediaIdBytes.size)
        buf.put(mediaIdBytes)
        buf.putInt(mel.size)
        for (v in mel) buf.putFloat(v)
        return buf.array()
    }

    /** Parse a serialized capture, or null when the magic/version doesn't match this store. */
    private fun decode(bytes: ByteArray): Pair<String, FloatArray>? {
        if (bytes.size < HEADER_INTS * 4) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.int != MAGIC) return null
        if (buf.int != captureFormatVersion) return null // stale capture format
        val idLen = buf.int
        // Bound against the bytes actually left in the buffer (not the whole file), so a corrupt/truncated
        // length can't under-read.
        if (idLen < 0 || idLen > buf.remaining()) return null
        val idBytes = ByteArray(idLen)
        buf.get(idBytes)
        val melLen = buf.int
        // Compare via division (remaining()/4) rather than melLen*4 so a huge melLen can't Int-overflow the
        // product to a negative value and slip past the guard into a multi-GB FloatArray allocation.
        if (melLen < 0 || melLen > buf.remaining() / 4) return null
        val mel = FloatArray(melLen)
        for (i in 0 until melLen) mel[i] = buf.float
        return String(idBytes, Charsets.UTF_8) to mel
    }

    companion object {
        private const val DIR_NAME = "rec-captures"
        private const val SUFFIX = ".mel"
        private const val MAGIC = 0x52434150 // "RCAP"

        /** Bump when the mel front-end contract changes so old cached captures are dropped. */
        const val CAPTURE_FORMAT_VERSION = 1

        /** 4 ints in the header before the mediaId block (magic, version, idLen, and the melLen). */
        private const val HEADER_INTS = 4

        private const val DEFAULT_MAX_ENTRIES = 64

        /** ~256 KB per mel (1001*64 float32) * 64 ≈ 16 MB ceiling. */
        private const val DEFAULT_MAX_BYTES = 24L * 1024 * 1024

        /** Test factory: back the store with an arbitrary [dir] (bounds overridable for eviction tests). */
        fun forDirectory(
            dir: File,
            captureFormatVersion: Int = CAPTURE_FORMAT_VERSION,
            maxEntries: Int = DEFAULT_MAX_ENTRIES,
            maxBytes: Long = DEFAULT_MAX_BYTES,
        ): PendingCaptureStore = PendingCaptureStore(dir, captureFormatVersion, maxEntries, maxBytes)
    }
}
