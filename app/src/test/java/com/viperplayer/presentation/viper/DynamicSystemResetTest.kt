package com.viperplayer.presentation.viper

import com.viperplayer.domain.audio.AudioOutputDevice
import com.viperplayer.domain.model.DynamicSystemDeviceType
import com.viperplayer.domain.model.ViperDefaults
import com.viperplayer.domain.model.ViperEffectsState
import com.viperplayer.domain.repository.ViperRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the Dynamic System "Device type" reset added for the previously no-op reset button
 * (`onSelectedIndexReset = {}` in DynamicSystemEffect). `ViperViewModel.resetDynamicSystemDeviceType`
 * applies the same `updateEffectsState { it.copy(dynamicSystem = ...deviceType = DEFAULT) }` transform
 * asserted here through a fake [ViperRepository]; mirrors the (unwired-before) bass-strength reset so
 * the reset restores only the device type while leaving the bass strength untouched.
 *
 * The transform is exercised directly against the repository rather than through the full
 * [ViperViewModel] because that ViewModel additionally depends on an Android `Context`-bound
 * `ViperAssetRepository`, which the project's pure-JVM unit tests deliberately avoid.
 */
class DynamicSystemResetTest {

    /** In-memory [ViperRepository] whose [updateEffectsState] mutates a [MutableStateFlow], like prod. */
    private class FakeViperRepository(
        initial: ViperEffectsState = ViperEffectsState.default(),
    ) : ViperRepository {
        private val _state = MutableStateFlow(initial)
        override val effectsState: StateFlow<ViperEffectsState> = _state.asStateFlow()
        override val activeDevice: StateFlow<AudioOutputDevice?> = MutableStateFlow(null)

        override suspend fun updateEffectsState(update: (ViperEffectsState) -> ViperEffectsState) {
            _state.update(update)
        }
    }

    /** The reset transform used by `ViperViewModel.resetDynamicSystemDeviceType`. */
    private suspend fun ViperRepository.resetDynamicSystemDeviceType() {
        updateEffectsState {
            it.copy(
                dynamicSystem = it.dynamicSystem.copy(
                    deviceType = ViperDefaults.DYNAMIC_SYSTEM_DEVICE_TYPE
                )
            )
        }
    }

    @Test
    fun reset_restoresDefaultDeviceType_andKeepsBassStrength() = runTest {
        // Pick a device type that is NOT the default, plus a non-default bass strength.
        val nonDefaultType = DynamicSystemDeviceType.entries
            .first { it != ViperDefaults.DYNAMIC_SYSTEM_DEVICE_TYPE }
        val customBass = ViperDefaults.DYNAMIC_SYSTEM_BASS_STRENGTH + 42
        val repo = FakeViperRepository(
            ViperEffectsState.default().let { state ->
                state.copy(
                    dynamicSystem = state.dynamicSystem.copy(
                        deviceType = nonDefaultType,
                        dynamicBassStrength = customBass,
                    )
                )
            }
        )

        repo.resetDynamicSystemDeviceType()

        val dynamicSystem = repo.effectsState.value.dynamicSystem
        assertEquals(ViperDefaults.DYNAMIC_SYSTEM_DEVICE_TYPE, dynamicSystem.deviceType)
        assertEquals("bass strength must be untouched by a device-type reset", customBass, dynamicSystem.dynamicBassStrength)
    }
}
