package com.viperplayer.presentation.listeningstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.stats.PlayHistoryDao
import com.viperplayer.data.stats.toPlayRecord
import com.viperplayer.domain.stats.ListeningStats
import com.viperplayer.domain.stats.ListeningStatsAggregator
import com.viperplayer.domain.stats.PlayRecord
import com.viperplayer.domain.stats.StatsRange
import com.viperplayer.domain.stats.WrappedSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject

/**
 * ViewModel for the Listening stats + Wrapped screens. All heavy lifting is delegated to the pure
 * [ListeningStatsAggregator]: this class only streams the raw events from the DAO, maps them, and
 * re-aggregates whenever the events or the selected [StatsRange] change. No aggregation logic lives
 * here (or in the screen) — the aggregator is the single, unit-tested source of truth.
 */
@HiltViewModel
class ListeningStatsViewModel @Inject constructor(
    private val playHistoryDao: PlayHistoryDao,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    private val records: StateFlow<List<PlayRecord>> = playHistoryDao.observeAll()
        .map { list -> list.map { it.toPlayRecord() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _range = MutableStateFlow(StatsRange.LAST_4_WEEKS)
    val range: StateFlow<StatsRange> = _range.asStateFlow()

    val hasHistory: StateFlow<Boolean> = playHistoryDao.observeCount()
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** The aggregated stats for the currently-selected range; recomputes on events or range change. */
    val stats: StateFlow<ListeningStats> = combine(records, _range) { recs, range ->
        ListeningStatsAggregator.aggregate(recs, range, System.currentTimeMillis(), zone)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ListeningStats.empty(StatsRange.LAST_4_WEEKS),
    )

    /**
     * The Wrapped (year-in-review) summary for the current calendar year. Reactive so it recomputes
     * once the DAO's events land (a freshly-created ViewModel on the Wrapped destination starts with
     * an empty [records] until the flow emits).
     */
    val wrapped: StateFlow<WrappedSummary> = records
        .map { recs -> ListeningStatsAggregator.wrapped(recs, Year.now(zone).value, zone) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ListeningStatsAggregator.wrapped(emptyList(), Year.now(zone).value, zone),
        )

    fun selectRange(range: StatsRange) {
        _range.value = range
    }

    /** Privacy: wipe all recorded listening history. */
    fun clearHistory() {
        viewModelScope.launch { playHistoryDao.clearAll() }
    }
}
