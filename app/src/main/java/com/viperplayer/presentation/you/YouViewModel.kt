package com.viperplayer.presentation.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.plugin.update.PluginUpdateManager
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.lastfm.LastfmRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.SharedPlaylistsRepository
import com.viperplayer.domain.social.SocialFeatures
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Snapshot of the Last.fm scrobbling status shown on the You hub's Last.fm row: whether a session is
 * authenticated and, if so, the username to render "Scrobbling as …".
 */
data class LastfmStatus(
    val connected: Boolean = false,
    val username: String? = null,
)

/**
 * State for the You hub. All values are derived from repositories in this ViewModel (MVVM house rule):
 * the screen only renders. Social fields are only meaningful when [socialEnabled]; when the backend is
 * unconfigured every social repository emits empty and the screen hides the SOCIAL section.
 */
data class YouUiState(
    val account: AccountState = AccountState(),
    val socialEnabled: Boolean = false,
    /** A friend currently listening (for the Friend-activity subtitle), or null. */
    val firstListeningFriendName: String? = null,
    /** How many friends beyond the first are listening now. */
    val otherListenersCount: Int = 0,
    /** Unread shared-playlist invites (drives the Shared-with-you badge). */
    val sharedUnreadCount: Int = 0,
    /** Connected plugin count for the Plugins subtitle. */
    val connectedPluginCount: Int = 0,
    /** Available plugin updates for the Plugins subtitle + alert dot. */
    val pluginUpdateCount: Int = 0,
    val lastfm: LastfmStatus = LastfmStatus(),
)

/**
 * ViewModel for the You hub. Observes the account, social, plugin and Last.fm surfaces and folds them
 * into a single [YouUiState]. No network/DB access happens here beyond observing existing repository
 * flows; the manual "sync now" action delegates to the plugin repository's existing refresh path.
 */
@HiltViewModel
class YouViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val pluginRepository: PluginRepository,
    private val pluginUpdateManager: PluginUpdateManager,
    private val lastfmRepository: LastfmRepository,
    private val friendActivityRepository: FriendActivityRepository,
    private val sharedPlaylistsRepository: SharedPlaylistsRepository,
    socialFeatures: SocialFeatures,
) : ViewModel() {

    private val socialEnabled = socialFeatures.enabled

    val uiState: StateFlow<YouUiState> = combine(
        accountRepository.state,
        friendActivityRepository.activity,
        sharedPlaylistsRepository.unreadCount,
        pluginRepository.connectedPlugins,
        combine(
            pluginUpdateManager.availableUpdates,
            lastfmRepository.settings,
        ) { updates, lastfm ->
            updates.size to LastfmStatus(
                connected = lastfm.isAuthenticated,
                username = lastfm.username,
            )
        },
    ) { account, activity, sharedUnread, connectedPlugins, updatesAndLastfm ->
        val (updateCount, lastfm) = updatesAndLastfm
        val listeningNow = activity.filterIsInstance<FriendActivityItem.ListeningNow>()
        YouUiState(
            account = account,
            socialEnabled = socialEnabled,
            firstListeningFriendName = listeningNow.firstOrNull()?.friend?.displayName,
            otherListenersCount = (listeningNow.size - 1).coerceAtLeast(0),
            sharedUnreadCount = sharedUnread,
            connectedPluginCount = connectedPlugins.size,
            pluginUpdateCount = updateCount,
            lastfm = lastfm,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState(socialEnabled = socialEnabled))

    /**
     * Manual library-sync trigger for the "Library synced" card's sync button. The dedicated
     * local↔backend reconciliation orchestration is a follow-up; here we re-run the app's existing
     * plugin refresh so connected sources (and their libraries) are re-queried.
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                pluginRepository.refreshPlugins()
            } catch (e: Exception) {
                Timber.w(e, "You hub: manual sync failed")
            }
        }
    }
}
