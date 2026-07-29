package com.viperplayer.data.rec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the ViPER backend recommender REST contract (P3; the `/recommendations` +
 * `/discovery/contribute` routes; github.com/iscle/viper-backend).
 *
 * Field-name casing is asymmetric and matched EXACTLY here: request bodies use snake_case
 * (`model_version`, `taste_vector`, `exclude_ids`, `accepted`) while response candidates use camelCase
 * (`pluginId`, `sourceId`). Every mismatched field is pinned with [SerialName] so a rename in the
 * Kotlin model can never silently break the contract.
 */

/** Request body for `POST /recommendations`. */
@Serializable
data class DiscoverRequestDto(
    @SerialName("model_version") val modelVersion: String,
    /** The device's warm 512-d L2-normalized CLAP taste vector. */
    @SerialName("taste_vector") val tasteVector: List<Float>,
    /** Ids the caller already owns, as `"pluginId:sourceId"`, to exclude from the feed. */
    @SerialName("exclude_ids") val excludeIds: List<String>,
    @SerialName("limit") val limit: Int,
)

/** Response body for `POST /recommendations`. */
@Serializable
data class DiscoverResponseDto(
    @SerialName("model_version") val modelVersion: String,
    @SerialName("candidates") val candidates: List<CandidateDto> = emptyList(),
)

/** A single recommended track (unowned discovery result). Response casing is camelCase. */
@Serializable
data class CandidateDto(
    @SerialName("pluginId") val pluginId: String,
    @SerialName("sourceId") val sourceId: String,
    @SerialName("title") val title: String,
    @SerialName("artist") val artist: String = "",
    @SerialName("album") val album: String = "",
    @SerialName("score") val score: Double = 0.0,
)

/** Request body for `POST /discovery/contribute` — anonymized L2-normalized taste vectors only, no ids. */
@Serializable
data class ContributeRequestDto(
    @SerialName("model_version") val modelVersion: String,
    @SerialName("vectors") val vectors: List<List<Float>>,
)

/** Response body for `POST /discovery/contribute`. */
@Serializable
data class ContributeResponseDto(
    @SerialName("accepted") val accepted: Int = 0,
)
