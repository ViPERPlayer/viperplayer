package com.viperplayer.presentation.social

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.SocialFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the Friend-activity feed screen (mockup 5a). Reads the recency-ordered feed from
 * [FriendActivityRepository], splits it into the mockup's two sections — the "listening now" rail (the
 * [FriendActivityItem.ListeningNow] items) and the "earlier" event list (everything else) — and folds
 * in the [SocialFeatures] gate so the screen can render a clean gated/empty state when the backend is
 * off. All grouping + relative-time formatting lives here; the composable only renders this state.
 */
@HiltViewModel
class FriendActivityViewModel @Inject constructor(
    socialFeatures: SocialFeatures,
    repository: FriendActivityRepository,
) : ViewModel() {

    val uiState: StateFlow<FriendActivityUiState> = repository.activity
        .map { items -> toState(enabled = socialFeatures.enabled, items = items) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FriendActivityUiState(
                enabled = socialFeatures.enabled,
                loading = socialFeatures.enabled,
            ),
        )

    private fun toState(enabled: Boolean, items: List<FriendActivityItem>): FriendActivityUiState {
        if (!enabled) return FriendActivityUiState(enabled = false, loading = false)
        val listeningNow = items.filterIsInstance<FriendActivityItem.ListeningNow>()
        val earlier = items.filterNot { it is FriendActivityItem.ListeningNow }
        return FriendActivityUiState(
            enabled = true,
            loading = false,
            listeningNow = listeningNow,
            earlier = earlier.map { it to relativeTime(it.timestampMs) },
        )
    }

    private fun relativeTime(timestampMs: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            timestampMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        )
}

/**
 * UI state for the Friend-activity feed. [enabled] mirrors the social feature gate; while [loading] the
 * screen shows a spinner (only meaningful when enabled). [listeningNow] and [earlier] are the two
 * rendered sections; [earlier] carries a pre-formatted relative-time label per item. Empty lists with
 * `enabled && !loading` is the "no activity yet" empty state.
 */
data class FriendActivityUiState(
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val listeningNow: List<FriendActivityItem.ListeningNow> = emptyList(),
    val earlier: List<Pair<FriendActivityItem, CharSequence>> = emptyList(),
) {
    val isEmpty: Boolean get() = listeningNow.isEmpty() && earlier.isEmpty()
}
