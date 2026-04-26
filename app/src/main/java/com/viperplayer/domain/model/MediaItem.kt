package com.viperplayer.domain.model

import android.os.Parcelable

sealed interface MediaItem : Parcelable {
    val id: MediaId
}
