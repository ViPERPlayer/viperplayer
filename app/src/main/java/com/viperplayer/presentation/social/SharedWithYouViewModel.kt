package com.viperplayer.presentation.social

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.social.SharedPlaylistInvite
import com.viperplayer.domain.social.SharedPlaylistsRepository
import com.viperplayer.domain.social.SocialFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Shared-with-you inbox screen (mockup 5b). Reads the invite inbox from
 * [SharedPlaylistsRepository], splits it into the mockup's two sections — the "new" (unread) invites
 * rendered as full action cards and the "earlier" (already-seen) invites rendered as compact saved
 * rows — and folds in the [SocialFeatures] gate. The two inbox mutations (save / dismiss) forward to
 * the repository. All grouping + relative-time formatting lives here; the composable only renders this
 * state and emits events.
 */
@HiltViewModel
class SharedWithYouViewModel @Inject constructor(
    socialFeatures: SocialFeatures,
    private val repository: SharedPlaylistsRepository,
) : ViewModel() {

    val uiState: StateFlow<SharedWithYouUiState> = repository.inbox
        .map { invites -> toState(enabled = socialFeatures.enabled, invites = invites) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SharedWithYouUiState(
                enabled = socialFeatures.enabled,
                loading = socialFeatures.enabled,
            ),
        )

    /** Accept the invite with the given [id] into the library. */
    fun save(id: String) {
        viewModelScope.launch { repository.save(id) }
    }

    /** Dismiss (decline) the invite with the given [id]. */
    fun dismiss(id: String) {
        viewModelScope.launch { repository.dismiss(id) }
    }

    private fun toState(enabled: Boolean, invites: List<SharedPlaylistInvite>): SharedWithYouUiState {
        if (!enabled) return SharedWithYouUiState(enabled = false, loading = false)
        val (new, earlier) = invites.partition { it.unread }
        val now = System.currentTimeMillis()
        return SharedWithYouUiState(
            enabled = true,
            loading = false,
            new = new.map { InviteUi(it, relativeTime(it.timestampMs, now)) },
            earlier = earlier.map { InviteUi(it, relativeTime(it.timestampMs, now)) },
        )
    }

    private fun relativeTime(timestampMs: Long, now: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            timestampMs,
            now,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
}

/**
 * A shared-playlist invite paired with its pre-formatted relative-time label, ready to render.
 */
data class InviteUi(
    val invite: SharedPlaylistInvite,
    val relativeTime: CharSequence,
)

/**
 * UI state for the Shared-with-you inbox. [enabled] mirrors the social feature gate; while [loading]
 * the screen shows a spinner (only meaningful when enabled). [new] are unread invites (full cards) and
 * [earlier] are already-seen invites (compact rows). Empty lists with `enabled && !loading` is the
 * "nothing shared yet" empty state.
 */
data class SharedWithYouUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val new: List<InviteUi> = emptyList(),
    val earlier: List<InviteUi> = emptyList(),
) {
    val isEmpty: Boolean get() = new.isEmpty() && earlier.isEmpty()
}
