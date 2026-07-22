package com.viperplayer.presentation.settings.about

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.viperplayer.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AboutSettingsUiState(
    val versionName: String = "Unknown",
    val versionCode: Long = 0L
)

@HiltViewModel
class AboutSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutSettingsUiState())
    val uiState: StateFlow<AboutSettingsUiState> = _uiState.asStateFlow()

    init {
        loadAppInfo()
    }

    private fun loadAppInfo() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val packageInfo = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    )
                    _uiState.update {
                        it.copy(
                            versionName = packageInfo.versionName
                                ?: context.getString(R.string.version_unknown),
                            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                packageInfo.longVersionCode
                            } else {
                                @Suppress("DEPRECATION")
                                packageInfo.versionCode.toLong()
                            }
                        )
                    }
                } catch (e: Exception) {
                    // Keep default values
                }
            }
        }
    }
}

