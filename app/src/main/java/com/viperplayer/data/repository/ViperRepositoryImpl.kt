package com.viperplayer.data.repository

import com.viperplayer.data.audio.AudioDeviceManager
import com.viperplayer.data.local.ViperPlayerDatabase
import com.viperplayer.data.local.dao.ViperPresetDao
import com.viperplayer.data.repository.mapper.ViperPresetMapper.toDomain
import com.viperplayer.data.repository.mapper.ViperPresetMapper.toEntity
import com.viperplayer.domain.audio.AudioOutputDevice
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.model.ViperPreset
import com.viperplayer.domain.repository.ViperRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ViperRepository that manages ViPER audio processing state.
 */
@Singleton
class ViperRepositoryImpl @Inject constructor(
    private val database: ViperPlayerDatabase,
    private val audioDeviceManager: AudioDeviceManager

) : ViperRepository {
    
    private val presetDao: ViperPresetDao = database.viperPresetDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Current effects state - initialized from default or loaded preset
    private val _effectsState = MutableStateFlow(ViperEffectsState.default())
    override val effectsState: StateFlow<ViperEffectsState> = _effectsState.asStateFlow()
    
    // Current active device ID (null = using default)
    private val _activeDevice = MutableStateFlow<AudioOutputDevice?>(null)
    override val activeDevice: StateFlow<AudioOutputDevice?> = _activeDevice.asStateFlow()
    
    init {
        // Observe audio device changes and automatically switch presets
        observeAudioDeviceChanges()
        // Observe effects state changes and auto-save with debounce
        observeAndPersistEffectsState()
    }
    
    /**
     * Observes effects state changes and automatically persists them with debounce.
     * This ensures all changes are saved, even if there's no active preset.
     */
    private fun observeAndPersistEffectsState() {
        _effectsState
            .drop(1) // Skip initial value for persistence
            .onEach { state ->

            }
            .debounce(1000) // Debounce persistence
            .onEach { state ->
                persistCurrentState(state)
            }
            .launchIn(repositoryScope)
            
        // Apply initial state

    }


    
    /**
     * Persists the current effects state.
     * If there's an active preset, updates it. Otherwise, creates or updates a default preset.
     */
    private suspend fun persistCurrentState(state: ViperEffectsState) {
        val currentDevice = _activeDevice.value

        if (currentDevice != null) {
            // Check if device has a preset, if so update it
            val existingPreset = presetDao.getPresetByDeviceId(currentDevice.id)
            if (existingPreset != null) {
                val updated = existingPreset.toDomain().copy(
                    effectsState = state,
                    updatedAt = System.currentTimeMillis()
                )
                presetDao.updatePreset(updated.toEntity())
                Timber.d("Auto-saved state to device preset: ${existingPreset.id}")
                return
            }

            // Create new preset for this device
            createDevicePreset(state, currentDevice)
        }
    }
    
    /**
     * Creates a new preset for the given device.
     */
    private suspend fun createDevicePreset(state: ViperEffectsState, device: AudioOutputDevice) {
        val preset = ViperPreset(
            name = device.name,
            effectsState = state,
            deviceId = device.id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val id = presetDao.insertPreset(preset.toEntity())
        Timber.d("Auto-created device preset: $id for device: ${device.id}")
    }
    
    /**
     * Observes audio device changes and automatically loads the appropriate preset.
     */
    private fun observeAudioDeviceChanges() {
        audioDeviceManager.currentDevice
            .onEach { device ->
                Timber.d("Audio device changed: $device")
                if (device != null) {
                    setActiveDevice(device)
                }
            }
            .launchIn(repositoryScope)
    }
    
    override suspend fun updateEffectsState(update: (ViperEffectsState) -> ViperEffectsState) {
        _effectsState.update(update)
    }
    
    private suspend fun setActiveDevice(device: AudioOutputDevice) {
        _activeDevice.value = device

        // Check if device has a preset
        val preset = presetDao.getPresetByDeviceId(device.id)
        Timber.d("Setting active device: $device, preset: $preset")
        if (preset != null) {
            // Load the device-specific preset
            loadPreset(preset.id)
        } else {
            // No preset for this device, use default state
            _effectsState.value = ViperEffectsState.default()
        }
    }
    
    // Preset management
    private suspend fun getPresetById(id: Long): ViperPreset? {
        return presetDao.getPresetById(id)?.toDomain()
    }

    private suspend fun loadPreset(presetId: Long) {
        val preset = getPresetById(presetId)
        if (preset == null) {
            Timber.e("Preset not found: $presetId")
            return
        }
        _effectsState.value = preset.effectsState
    }
}


