package com.viperplayer.follows.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.MediaId
import com.viperplayer.follows.data.FollowedArtistsRepository
import com.viperplayer.follows.domain.FollowedArtist
import com.viperplayer.follows.domain.FollowedArtistSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Following screen. Streams the followed artists (alphabetical by name) and
 * forwards unfollow to the repository. All list logic lives in the repository/ordering helper.
 */
@HiltViewModel
class FollowingViewModel @Inject constructor(
    private val repository: FollowedArtistsRepository,
) : ViewModel() {

    val followedArtists: StateFlow<List<FollowedArtist>> =
        repository.followedArtists(FollowedArtistSort.NAME)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

    fun unfollow(mediaId: MediaId) {
        viewModelScope.launch {
            repository.unfollow(mediaId)
        }
    }
}
