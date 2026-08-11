package com.viperplayer.data.rec

import com.viperplayer.domain.rec.ClapModelState
import com.viperplayer.domain.rec.FailureReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure CLAP model state machine ([ClapModelStateMachine.derive]) — the fold from
 * (installed version, on-disk file, worker snapshot) into the observable [ClapModelState]. Covers the
 * precedence rules the Settings toggle depends on.
 */
class ClapModelStateMachineTest {

    private val expected = ClapModel.MODEL_VERSION
    private val installedFile = File("/data/rec-models/clap_audio_$expected.onnx")

    @Test
    fun absent_whenNothingInstalledAndNoWork() {
        val state = ClapModelStateMachine.derive(
            installedVersion = null,
            installedFile = null,
            work = null,
        )
        assertEquals(ClapModelState.Absent, state)
    }

    @Test
    fun ready_whenInstalledMatchesAppAndFilePresent() {
        val state = ClapModelStateMachine.derive(
            installedVersion = expected,
            installedFile = installedFile,
            work = ModelWorkSnapshot(phase = WorkPhase.IDLE),
        )
        assertEquals(ClapModelState.Ready(expected, installedFile), state)
    }

    @Test
    fun absent_whenVersionRecordedButFileMissing() {
        // The version pref survived but the file was cleared → needs re-download, shown as Absent.
        val state = ClapModelStateMachine.derive(
            installedVersion = expected,
            installedFile = null,
            work = null,
        )
        assertEquals(ClapModelState.Absent, state)
    }

    @Test
    fun versionMismatch_whenInstalledIsStaleRelativeToApp() {
        val state = ClapModelStateMachine.derive(
            installedVersion = "laion-larger_clap_music-mel0",
            installedFile = File("/old.onnx"),
            work = null,
        )
        assertEquals(
            ClapModelState.VersionMismatch("laion-larger_clap_music-mel0", expected),
            state,
        )
    }

    @Test
    fun downloading_whenWorkerRunning_carriesProgress() {
        val state = ClapModelStateMachine.derive(
            installedVersion = null,
            installedFile = null,
            work = ModelWorkSnapshot(phase = WorkPhase.RUNNING, progress = 0.42f),
        )
        assertTrue(state is ClapModelState.Downloading)
        assertEquals(0.42f, (state as ClapModelState.Downloading).progress)
    }

    @Test
    fun downloading_indeterminate_whenEnqueuedWithNoProgress() {
        val state = ClapModelStateMachine.derive(
            installedVersion = null,
            installedFile = null,
            work = ModelWorkSnapshot(phase = WorkPhase.ENQUEUED, progress = null),
        )
        assertTrue(state is ClapModelState.Downloading)
        assertNull((state as ClapModelState.Downloading).progress)
    }

    @Test
    fun runningWorker_takesPrecedenceOverInstalledReady() {
        // A re-download in flight over an already-installed model still shows Downloading.
        val state = ClapModelStateMachine.derive(
            installedVersion = expected,
            installedFile = installedFile,
            work = ModelWorkSnapshot(phase = WorkPhase.RUNNING, progress = 0.1f),
        )
        assertTrue(state is ClapModelState.Downloading)
    }

    @Test
    fun failed_carriesReason() {
        val state = ClapModelStateMachine.derive(
            installedVersion = null,
            installedFile = null,
            work = ModelWorkSnapshot(phase = WorkPhase.FAILED, failureReason = FailureReason.CHECKSUM_MISMATCH),
        )
        assertEquals(ClapModelState.Failed(FailureReason.CHECKSUM_MISMATCH), state)
    }

    @Test
    fun failed_defaultsToNetworkReason_whenUnspecified() {
        val state = ClapModelStateMachine.derive(
            installedVersion = null,
            installedFile = null,
            work = ModelWorkSnapshot(phase = WorkPhase.FAILED, failureReason = null),
        )
        assertEquals(ClapModelState.Failed(FailureReason.NETWORK_OR_IO), state)
    }

    @Test
    fun idleWorker_fallsThroughToInstalledState() {
        // A succeeded/cancelled worker (IDLE) does not mask the resting Ready state.
        val state = ClapModelStateMachine.derive(
            installedVersion = expected,
            installedFile = installedFile,
            work = ModelWorkSnapshot(phase = WorkPhase.IDLE),
        )
        assertEquals(ClapModelState.Ready(expected, installedFile), state)
    }
}
