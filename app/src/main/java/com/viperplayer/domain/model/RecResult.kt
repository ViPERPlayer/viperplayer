package com.viperplayer.domain.model

/**
 * Outcome of a recommendation request. The UI renders one of three cases so it can be honest about
 * *how* the songs were produced (on-device kNN vs the plugin's related-songs fallback) and give a clear
 * empty state:
 *
 *  - [Local]    — the on-device audio-embedding kNN produced these songs (the intended experience).
 *  - [Fallback] — no local embedding was usable (seed not indexed / streaming-only, or too few local
 *                 results), so we fell back to the source plugin's `getRelatedSongs`. The UI notes this
 *                 subtly so the ranking's provenance is clear.
 *  - [Empty]    — nothing could be produced; [reason] tells the UI which explanatory state to show.
 */
sealed interface RecResult {

    /** kNN over the local embedding index produced [songs] (best-first). */
    data class Local(val songs: List<Song>) : RecResult

    /** The plugin's related-songs feed produced [songs] (best-effort ordering from the plugin). */
    data class Fallback(val songs: List<Song>) : RecResult

    /** Nothing to show; [reason] drives the explanatory empty state. */
    data class Empty(val reason: RecEmptyReason) : RecResult
}

/**
 * Why a [RecResult.Empty] has no songs — kept UI-string-free (the presentation layer localizes each) so
 * this stays a pure domain type.
 */
enum class RecEmptyReason {
    /** Smart recommendations are turned off in Settings. */
    RECOMMENDATIONS_DISABLED,

    /** The library isn't embedded yet (model not ready and/or indexing still running). */
    NOT_INDEXED_YET,

    /** Everything ran but produced no candidates (empty library / no related songs). */
    NO_RESULTS,

    /** An unexpected error occurred while computing recommendations. */
    ERROR,
}

/**
 * Snapshot of whether the on-device recommender can produce *local* results right now, so a screen can
 * choose the right empty/explanatory state without reaching into the model/index repositories itself.
 *
 * @param recommendationsEnabled the Settings opt-in.
 * @param modelReady a compatible CLAP model is installed and usable.
 * @param indexedCount how many library songs currently have a usable embedding.
 * @param indexingProcessed songs processed by the active indexing run, when one is running (else null).
 * @param indexingTotal the active run's target count, when one is running (else null).
 */
data class RecReadiness(
    val recommendationsEnabled: Boolean,
    val modelReady: Boolean,
    val indexedCount: Int,
    val indexingProcessed: Int? = null,
    val indexingTotal: Int? = null,
) {
    /** True while a background indexing run is in progress (drives the "Analyzing your library… N/M" line). */
    val isIndexing: Boolean get() = indexingProcessed != null && indexingTotal != null

    /** True once at least one song is embedded (local kNN can return something). */
    val hasIndex: Boolean get() = indexedCount > 0
}
