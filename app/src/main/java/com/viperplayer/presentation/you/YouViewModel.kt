package com.viperplayer.presentation.you

import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.plugin.update.PluginUpdateManager
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.data.stats.toPlayRecord
import com.viperplayer.data.sync.LibrarySync
import com.viperplayer.data.sync.LibrarySyncStatusStore
import com.viperplayer.domain.account.AccountRepository
import com.viperplayer.domain.account.AccountState
import com.viperplayer.domain.lastfm.LastfmRepository
import com.viperplayer.domain.repository.PluginRepository
import com.viperplayer.domain.repository.SettingsRepository
import com.viperplayer.domain.social.FriendActivityItem
import com.viperplayer.domain.social.FriendActivityRepository
import com.viperplayer.domain.social.SharedPlaylistsRepository
import com.viperplayer.domain.social.SocialFeatures
import com.viperplayer.domain.stats.ListeningStatsAggregator
import com.viperplayer.domain.sync.LibrarySyncStatus
import com.viperplayer.presentation.listeningstats.StatsFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.ZoneId
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
 * Last local-sync facts for the You hub's Library-synced card, formatted for display: aggregate counts
 * and a relative "updated N min ago" label (the relative label is formatted in the ViewModel because
 * it needs Android's DateUtils; the composable templates the surrounding text via stringResource).
 */
data class LibrarySyncStatusUi(
    val hasSynced: Boolean = false,
    val relativeTime: String = "",
    val songs: Int = 0,
    val albums: Int = 0,
)

/**
 * State for the You hub. All values are derived from repositories in this ViewModel (MVVM house rule):
 * the screen only renders. Social fields are only meaningful when [socialEnabled]; when the backend is
 * unconfigured every social repository emits empty and the screen hides the SOCIAL section.
 *
 * @param librarySyncStatus counts + relative-time for the Library-synced card ([LibrarySyncStatusUi]).
 * @param listeningThisMonth pre-formatted actual-listening time this month (e.g. "3h 20m"), or "" when
 *   nothing was played this month; the row hides its subtitle when empty.
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
    val librarySyncStatus: LibrarySyncStatusUi = LibrarySyncStatusUi(),
    val listeningThisMonth: String = "",
    /** Smart-recommendations opt-in — gates the "For You" row in the Music section. */
    val recommendationsEnabled: Boolean = false,
)

/**
 * ViewModel for the You hub. Observes the account, social, plugin, Last.fm, library-sync-status and
 * listening-stats surfaces and folds them into a single [YouUiState]. No aggregation/persistence logic
 * lives here beyond formatting (delegated to the pure aggregator + [StatsFormat]); the manual "sync
 * now" action delegates to the real local library-sync path.
 */
@HiltViewModel
class YouViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val pluginRepository: PluginRepository,
    private val pluginUpdateManager: PluginUpdateManager,
    private val lastfmRepository: LastfmRepository,
    private val friendActivityRepository: FriendActivityRepository,
    private val sharedPlaylistsRepository: SharedPlaylistsRepository,
    private val librarySync: LibrarySync,
    librarySyncStatusStore: LibrarySyncStatusStore,
    playHistoryDao: PlayHistoryDao,
    settingsRepository: SettingsRepository,
    socialFeatures: SocialFeatures,
) : ViewModel() {

    private val socialEnabled = socialFeatures.enabled
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Total actually-listened time this calendar month, as a "3h 20m" label ("" when nothing played). */
    private val listeningThisMonth: StateFlow<String> = playHistoryDao.observeAll()
        .map { entities ->
            val records = entities.map { it.toPlayRecord() }
            val ms = ListeningStatsAggregator.listenedMsInMonth(records, System.currentTimeMillis(), zone)
            if (ms <= 0L) "" else StatsFormat.listeningTime(ms)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val uiState: StateFlow<YouUiState> = combine(
        accountRepository.state,
        friendActivityRepository.activity,
        sharedPlaylistsRepository.unreadCount,
        pluginRepository.connectedPlugins,
        combine(
            pluginUpdateManager.availableUpdates,
            lastfmRepository.settings,
            librarySyncStatusStore.status,
            listeningThisMonth,
            settingsRepository.recommendationsEnabled,
        ) { updates, lastfm, syncStatus, thisMonth, recommendationsEnabled ->
            YouDerived(
                updateCount = updates.size,
                lastfm = LastfmStatus(
                    connected = lastfm.isAuthenticated,
                    username = lastfm.username,
                ),
                librarySyncStatus = syncStatus.toUi(),
                listeningThisMonth = thisMonth,
                recommendationsEnabled = recommendationsEnabled,
            )
        },
    ) { account, activity, sharedUnread, connectedPlugins, derived ->
        val listeningNow = activity.filterIsInstance<FriendActivityItem.ListeningNow>()
        YouUiState(
            account = account,
            socialEnabled = socialEnabled,
            firstListeningFriendName = listeningNow.firstOrNull()?.friend?.displayName,
            otherListenersCount = (listeningNow.size - 1).coerceAtLeast(0),
            sharedUnreadCount = sharedUnread,
            connectedPluginCount = connectedPlugins.size,
            pluginUpdateCount = derived.updateCount,
            lastfm = derived.lastfm,
            librarySyncStatus = derived.librarySyncStatus,
            listeningThisMonth = derived.listeningThisMonth,
            recommendationsEnabled = derived.recommendationsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState(socialEnabled = socialEnabled))

    /**
     * Manual library-sync trigger for the "Library synced" card's sync button — runs the real local
     * plugin-account → on-device pull across every connected plugin. All iteration + persistence lives
     * in the [LibrarySync] manager; here we just launch it.
     */
    fun syncNow() {
        viewModelScope.launch {
            try {
                librarySync.syncConnectedPlugins()
            } catch (e: Exception) {
                Timber.w(e, "You hub: manual sync failed")
            }
        }
    }

    private fun LibrarySyncStatus.toUi(): LibrarySyncStatusUi = LibrarySyncStatusUi(
        hasSynced = hasSynced,
        relativeTime = if (hasSynced) {
            DateUtils.getRelativeTimeSpanString(
                lastSyncedAtMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
        } else "",
        songs = songs,
        albums = albums,
    )

    /** Intermediate fold of the four inner flows (combine tops out at five typed args). */
    private data class YouDerived(
        val updateCount: Int,
        val lastfm: LastfmStatus,
        val librarySyncStatus: LibrarySyncStatusUi,
        val listeningThisMonth: String,
        val recommendationsEnabled: Boolean,
    )
}
