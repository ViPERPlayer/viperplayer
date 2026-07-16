package com.viperplayer.domain.model

/**
 * The full tag / metadata detail read from a single local audio file, like a dedicated tag viewer:
 * every common text tag plus the technical/audio properties of the file itself. Purely a data holder;
 * it is produced off the main thread by a [com.viperplayer.domain.repository.TagDetailsReader] and
 * rendered read-only by the Tag-details screen.
 *
 * Every field is nullable/optional and only carries a value when it is actually present in the file —
 * the screen omits empties, so absence is meaningful. Text-tag fields mirror the ID3v2 / Vorbis frames
 * the reader understands; technical fields come from the file and `MediaMetadataRetriever`.
 */
data class TagDetails(
    // ---- Text tags ----
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val date: String? = null,
    /** Track position, e.g. 5 of 12 (total may be absent). */
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    /** Disc position, e.g. 1 of 2 (total may be absent). */
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val comment: String? = null,
    val encoder: String? = null,

    // ---- Technical / audio properties ----
    /** Display file name (e.g. "01 - Song.flac"). */
    val fileName: String? = null,
    /** Absolute file-system path, when resolvable from MediaStore. */
    val filePath: String? = null,
    /** Friendly container/format name (e.g. "FLAC", "MP3", "OGG"). */
    val container: String? = null,
    val mimeType: String? = null,
    /** File size in bytes. */
    val fileSizeBytes: Long? = null,
    val durationMs: Long? = null,
    /** Overall bitrate in bits per second (as reported by the retriever). */
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitsPerSample: Int? = null,
    /** Size in bytes of the embedded cover art, when present and cheaply available. */
    val coverArtBytes: Int? = null,
) {
    /** True when any text tag is present (drives showing/hiding the whole "Tags" section). */
    fun hasAnyTextTag(): Boolean =
        title != null || artists.isNotEmpty() || album != null || albumArtist != null ||
            composer != null || genre != null || date != null || trackNumber != null ||
            discNumber != null || comment != null || encoder != null

    /** True when any technical property is present (drives the "File" / "Audio" sections). */
    fun hasAnyTechnical(): Boolean =
        fileName != null || filePath != null || container != null || mimeType != null ||
            fileSizeBytes != null || durationMs != null || bitrate != null ||
            sampleRate != null || channelCount != null || bitsPerSample != null ||
            coverArtBytes != null

    fun hasAny(): Boolean = hasAnyTextTag() || hasAnyTechnical()

    companion object {
        val EMPTY = TagDetails()
    }
}
