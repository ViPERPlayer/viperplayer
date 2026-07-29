package com.viperplayer.presentation.settings.content

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.data.rec.ClapModelRepository
import com.viperplayer.data.rec.ClapModelState
import com.viperplayer.data.rec.FailureReason
import com.viperplayer.data.rec.IndexingStatus
import com.viperplayer.data.rec.RecommendationIndexRepository
import com.viperplayer.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI-facing status of the smart-recommendations feature, derived from the persisted opt-in flag and
 * the CLAP model download state. Kept string-free (the composable maps each case to a localized
 * string) so the mapping logic stays out of the view.
 */
sealed interface RecommendationsUiStatus {
    /** The toggle is off. */
    data object Off : RecommendationsUiStatus

    /** Enabled and a model is installed + ready. */
    data object Ready : RecommendationsUiStatus

    /**
     * Enabled, model ready, and the background indexer is analyzing the library: [processed] of
     * [total] songs embedded so far. Shown as "Analyzing your library… N/M".
     */
    data class Indexing(val processed: Int, val total: Int) : RecommendationsUiStatus

    /** Enabled; the model download is enqueued but waiting on the Wi-Fi constraint. */
    data object WaitingForWifi : RecommendationsUiStatus

    /** Enabled; downloading. [percent] is 0..100, or null when indeterminate. */
    data class Downloading(val percent: Int?) : RecommendationsUiStatus

    /** Enabled but the app is too old to use the model the server now advertises. */
    data object AppOutdated : RecommendationsUiStatus

    /** Enabled; the last download attempt failed with [reason] (retryable). */
    data class Failed(val reason: FailureReason) : RecommendationsUiStatus
}

data class ContentSettingsUiState(
    val showExplicitContent: Boolean = true,
    val offlineMode: Boolean = false,
    val autoDownloadOnLike: Boolean = false,
    val recommendationsEnabled: Boolean = false,
    val recommendationsStatus: RecommendationsUiStatus = RecommendationsUiStatus.Off,
    /** Opt-in to contributing the anonymized taste vector to improve discovery (default OFF). */
    val contributeAnonymizedTaste: Boolean = false,
)

@HiltViewModel
class ContentSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val clapModelRepository: ClapModelRepository,
    private val recommendationIndexRepository: RecommendationIndexRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContentSettingsUiState())
    val uiState: StateFlow<ContentSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.showExplicitContent.collect { enabled ->
                _uiState.update { it.copy(showExplicitContent = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.offlineMode.collect { enabled ->
                _uiState.update { it.copy(offlineMode = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoDownloadOnLike.collect { enabled ->
                _uiState.update { it.copy(autoDownloadOnLike = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.contributeAnonymizedTaste.collect { enabled ->
                _uiState.update { it.copy(contributeAnonymizedTaste = enabled) }
            }
        }
        // Combine the opt-in flag with the live model state + indexing progress into a single UI status.
        viewModelScope.launch {
            combine(
                settingsRepository.recommendationsEnabled,
                clapModelRepository.modelState,
                recommendationIndexRepository.indexingStatus,
            ) { enabled, modelState, indexing ->
                enabled to toStatus(enabled, modelState, indexing)
            }.collect { (enabled, status) ->
                _uiState.update { it.copy(recommendationsEnabled = enabled, recommendationsStatus = status) }
            }
        }
    }

    fun setShowExplicitContent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowExplicitContent(enabled) }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setOfflineMode(enabled) }
    }

    fun setAutoDownloadOnLike(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDownloadOnLike(enabled) }
    }

    /**
     * Toggles smart recommendations. Enabling persists the opt-in and kicks off the (Wi-Fi-gated)
     * model download; disabling persists the opt-out and cancels/removes the model.
     */
    fun setRecommendationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRecommendationsEnabled(enabled)
            if (enabled) {
                clapModelRepository.ensureDownloaded()
            } else {
                clapModelRepository.deleteModel()
            }
        }
    }

    /** Retries a failed download (only meaningful while enabled). */
    fun retryRecommendationsDownload() {
        viewModelScope.launch { clapModelRepository.enqueueDownload() }
    }

    /** Toggles the anonymized-taste discovery contribution opt-in (default OFF; vectors only, no ids). */
    fun setContributeAnonymizedTaste(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setContributeAnonymizedTaste(enabled) }
    }

    /**
     * Maps the opt-in flag + model state + indexing progress into the UI status. Pure; no I/O. Once the
     * model is [ClapModelState.Ready], an active library index surfaces as [RecommendationsUiStatus.Indexing]
     * ("Analyzing your library… N/M"); when there's nothing left to index it settles on [Ready].
     */
    private fun toStatus(
        enabled: Boolean,
        modelState: ClapModelState,
        indexing: IndexingStatus,
    ): RecommendationsUiStatus {
        if (!enabled) return RecommendationsUiStatus.Off
        return when (modelState) {
            ClapModelState.Absent -> RecommendationsUiStatus.WaitingForWifi
            is ClapModelState.Downloading ->
                if (modelState.progress == null) {
                    RecommendationsUiStatus.WaitingForWifi
                } else {
                    RecommendationsUiStatus.Downloading((modelState.progress * 100).toInt())
                }
            is ClapModelState.Ready -> when (indexing) {
                is IndexingStatus.Indexing ->
                    RecommendationsUiStatus.Indexing(indexing.processed, indexing.total)
                IndexingStatus.Idle -> RecommendationsUiStatus.Ready
            }
            is ClapModelState.Failed -> RecommendationsUiStatus.Failed(modelState.reason)
            is ClapModelState.VersionMismatch -> RecommendationsUiStatus.AppOutdated
        }
    }
}
