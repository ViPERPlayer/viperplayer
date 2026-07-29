package com.viperplayer.data.repository

import com.viperplayer.data.account.AccountApiResult
import com.viperplayer.data.rec.CandidateDto
import com.viperplayer.data.rec.ClapModel
import com.viperplayer.data.rec.RecommendationApi
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.model.ArtistRef
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RecEmptyReason
import com.viperplayer.domain.model.RecReason
import com.viperplayer.domain.model.RecResult
import com.viperplayer.domain.model.Song
import com.viperplayer.domain.rec.TasteRepository
import com.viperplayer.domain.repository.DiscoveryRepository
import com.viperplayer.domain.repository.MediaLibraryRepository
import com.viperplayer.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-backed [DiscoveryRepository]: fetches unowned discovery candidates from the ViPER backend
 * recommender ([RecommendationApi]) ranked against the device's on-device CLAP taste vector, and
 * (opt-in) contributes the anonymized taste vector back to improve global discovery.
 *
 * All the network/auth/mapping glue lives here, per the app's MVVM rule. Every path degrades to a
 * clear [RecResult.Empty] and never throws to the caller (mirrors
 * [RecommendationRepositoryImpl]'s `runCatchingRec` discipline): the smart-recommendations opt-in and
 * a warm taste vector are prerequisites; a model-version handshake mismatch (409) or a network/auth
 * failure surfaces as a graceful empty, not an error.
 *
 * Auth: each backend call runs through [AccountRepository.withBackendAuth] — the shared
 * refresh-and-retry token seam — so no token handling is duplicated here.
 */
@Singleton
class DiscoveryRepositoryImpl @Inject constructor(
    private val recommendationApi: RecommendationApi,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tasteRepository: TasteRepository,
    private val mediaLibraryRepository: MediaLibraryRepository,
) : DiscoveryRepository {

    override suspend fun discover(limit: Int): RecResult {
        if (limit <= 0) return RecResult.Empty(RecEmptyReason.NO_RESULTS)

        // 1) Opt-in gate: discovery reuses the smart-recommendations model/taste, so it needs the opt-in.
        if (!recommendationsEnabled()) return RecResult.Empty(RecEmptyReason.RECOMMENDATIONS_DISABLED)

        // 2) A warm taste vector is required (the request is ranked against it). Cold ⇒ "still learning".
        val taste = runCatchingRec("read taste") { tasteRepository.taste() }
        val tasteVector = taste?.vector ?: return RecResult.Empty(RecEmptyReason.NOT_INDEXED_YET)

        // 3) Exclude everything the user already owns AND every thumbed-down (suppressed) id, so results
        //    are genuinely NEW and the backend never spends a page slot on a candidate we'd only drop.
        val suppressed = runCatchingRec("read suppressed ids") {
            tasteRepository.suppressedDiscoveryIds()
        }.orEmpty()
        val excludeIds = (ownedExcludeIds() + suppressed).distinct()

        // 4) Ask the backend. Auth-wrapped; a 409 handshake mismatch or any transport failure is graceful.
        val apiResult = runCatchingRec("discover request") {
            accountRepository.withBackendAuth { token ->
                // Adapt the recommender's own result into AccountApiResult so it flows through the token
                // seam's refresh-and-retry (401 → Unauthenticated triggers exactly one refresh + retry).
                when (val r = recommendationApi.discover(token, ClapModel.MODEL_VERSION, tasteVector, excludeIds, limit)) {
                    is RecommendationApi.DiscoverResult.Success -> AccountApiResult.Success(r.response.candidates)
                    RecommendationApi.DiscoverResult.HandshakeMismatch ->
                        AccountApiResult.Rejected(HANDSHAKE_MISMATCH)
                    RecommendationApi.DiscoverResult.NotConfigured -> AccountApiResult.NotConfigured
                    RecommendationApi.DiscoverResult.Error -> AccountApiResult.NetworkError
                }
            }
        }

        val candidates = when (apiResult) {
            is AccountApiResult.Success -> apiResult.value
            // Handshake mismatch, unauthenticated, network error, not-configured, or a swallowed
            // exception (null) all mean "nothing to show right now" — a retryable empty, never an error
            // thrown to the UI.
            else -> return RecResult.Empty(RecEmptyReason.NO_RESULTS)
        }

        // Defensively drop any suppressed candidate the backend still returned (it's asked to exclude
        // them above, but the client filter guarantees a thumbed-down id never reappears regardless).
        val songs = candidates.mapNotNull { it.toSongOrNull() }
            .filter { song ->
                val id = song.id
                id !is MediaId.Plugin || "${id.pluginId}:${id.sourceId}" !in suppressed
            }
        if (songs.isEmpty()) return RecResult.Empty(RecEmptyReason.NO_RESULTS)
        // Backend-ranked (best-first); modeled as a Fallback so the UI can note the ranking's provenance
        // (server, not the on-device kNN) exactly like the plugin related-songs fallback does. Each result
        // carries the GENERIC taste-based reason — we can't compute a specific anchor without embeddings.
        val reasons = songs.associate { it.id to RecReason(RecReason.Kind.MATCHES_TASTE) }
        return RecResult.Fallback(songs, reasons)
    }

    override suspend fun contributeTasteIfEnabled() {
        runCatchingRec("contribute taste") {
            if (!contributeAnonymizedTaste()) return
            val vector = tasteRepository.taste().vector ?: return // cold: nothing meaningful to share
            accountRepository.withBackendAuth { token ->
                when (recommendationApi.contribute(token, ClapModel.MODEL_VERSION, listOf(vector))) {
                    is RecommendationApi.ContributeResult.Success -> AccountApiResult.Success(Unit)
                    RecommendationApi.ContributeResult.NotConfigured -> AccountApiResult.NotConfigured
                    RecommendationApi.ContributeResult.Error -> AccountApiResult.NetworkError
                }
            }
        }
    }

    override suspend fun sendFeedback(mediaId: MediaId, positive: Boolean) {
        if (positive) {
            // Thumbs up = like the discovered song (reuses the normal like path).
            runCatchingRec("thumbs up like") { mediaLibraryRepository.setSongLiked(mediaId, true) }
            return
        }
        // Thumbs down: no on-device embedding for a server candidate, so suppress its id from the feed.
        val id = mediaId as? MediaId.Plugin ?: return
        runCatchingRec("suppress discovery id") {
            tasteRepository.suppressDiscoveryId("${id.pluginId}:${id.sourceId}")
        }
    }

    // --- helpers -----------------------------------------------------------------------------------

    /**
     * The exclude-id set the request sends: every OWNED (saved or liked) song that lives on a plugin,
     * as `"pluginId:sourceId"`. Local and radio ids are skipped (they aren't catalog-addressable on the
     * backend). Deduped; never throws.
     */
    private suspend fun ownedExcludeIds(): List<String> {
        val saved = runCatchingRec("load saved songs") {
            mediaLibraryRepository.getAllSavedSongs().first()
        }.orEmpty()
        val liked = runCatchingRec("load liked songs") {
            mediaLibraryRepository.getAllLikedSongs().first()
        }.orEmpty()
        return (saved + liked)
            .asSequence()
            .map { it.id }
            .filterIsInstance<MediaId.Plugin>()
            .map { "${it.pluginId}:${it.sourceId}" }
            .distinct()
            .toList()
    }

    /**
     * Maps a backend [CandidateDto] to a playable domain [Song]. The id is a [MediaId.Plugin]
     * `(pluginId, sourceId)`, so tapping the result routes through the normal plugin `getStream` path.
     * A blank pluginId/sourceId/title can't be played or addressed and is dropped (`MediaId.Plugin`
     * also requires a non-blank sourceId, hence the guard).
     */
    private fun CandidateDto.toSongOrNull(): Song? {
        if (pluginId.isBlank() || sourceId.isBlank() || title.isBlank()) return null
        return Song(
            id = MediaId.Plugin(pluginId = pluginId, sourceId = sourceId),
            title = title,
            artists = artist.takeIf { it.isNotBlank() }?.let { listOf(ArtistRef(name = it)) }.orEmpty(),
            requiresInternet = true,
        )
    }

    private suspend fun recommendationsEnabled(): Boolean =
        runCatchingRec("read rec setting") { settingsRepository.recommendationsEnabled.first() } ?: false

    private suspend fun contributeAnonymizedTaste(): Boolean =
        runCatchingRec("read contribute setting") {
            settingsRepository.contributeAnonymizedTaste.first()
        } ?: false

    /** Runs [block], logging and swallowing any failure to null so discovery never crashes. */
    private inline fun <T> runCatchingRec(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Discovery step failed: %s", what)
            null
        }

    private companion object {
        /** Sentinel [AccountApiResult.Rejected] message for a 409 model-version handshake mismatch. */
        const val HANDSHAKE_MISMATCH = "handshake_mismatch"
    }
}
