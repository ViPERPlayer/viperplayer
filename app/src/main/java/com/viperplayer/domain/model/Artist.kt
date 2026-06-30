package com.viperplayer.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents an artist.
 * Based on AIDL Artist model - only contains fields that exist in the plugin API.
 */
@Serializable
@Parcelize
@Immutable
data class Artist(
    override val id: MediaId,
    val name: String,
    val imageUrl: String? = null
) : MediaItem, Parcelable
