package com.viperplayer.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.TagDetails
import com.viperplayer.domain.repository.TagDetailsReader
import com.viperplayer.presentation.navigation.TagDetails as TagDetailsRoute
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the Tag-details screen. Loading → either Loaded(details) or Empty (nothing readable). */
sealed interface TagDetailsUiState {
    val title: String
    data class Loading(override val title: String) : TagDetailsUiState
    data class Loaded(override val title: String, val details: TagDetails) : TagDetailsUiState
    data class Empty(override val title: String) : TagDetailsUiState
}

/**
 * Loads the full tag / metadata detail for a local song via [TagDetailsReader] (which does all the
 * `ContentResolver` / `MediaMetadataRetriever` / byte-parsing off the main thread). The screen only
 * renders [uiState]; no reading happens in the UI layer.
 */
@HiltViewModel(assistedFactory = TagDetailsViewModel.Factory::class)
class TagDetailsViewModel @AssistedInject constructor(
    @Assisted private val route: TagDetailsRoute,
    private val tagDetailsReader: TagDetailsReader,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: TagDetailsRoute): TagDetailsViewModel
    }

    private val mediaId: MediaId = route.mediaId

    private val _uiState = MutableStateFlow<TagDetailsUiState>(TagDetailsUiState.Loading(route.initialTitle))
    val uiState: StateFlow<TagDetailsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val details = tagDetailsReader.read(mediaId)
            _uiState.update { current ->
                // Prefer the parsed title for the app bar once known, else keep the nav placeholder.
                val title = details?.title ?: current.title
                if (details != null && details.hasAny()) {
                    TagDetailsUiState.Loaded(title, details)
                } else {
                    TagDetailsUiState.Empty(title)
                }
            }
        }
    }
}
