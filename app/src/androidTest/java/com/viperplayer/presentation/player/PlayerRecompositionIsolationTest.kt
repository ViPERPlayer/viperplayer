package com.viperplayer.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.viperplayer.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Proves the player's recomposition isolation. The player's dynamic controls each read their own value
 * INSIDE the leaf that renders it (in production a `ConnectedX` leaf collects its own StateFlow; the
 * value it reads is a Compose [State]). A [State] read inside a composable makes exactly that composable
 * the recomposition scope woken when the value changes.
 *
 * This test reconstructs that exact shape: a set of isolated leaves (mirroring the real
 * `ConnectedLikeButton` / `ConnectedShuffleButton` / `ConnectedRepeatButton` / `ConnectedPlayPauseButton`
 * / `ConnectedSeekBar` and the artwork / title regions), each reading its own [State] and bumping a
 * body-level recomposition counter, all under one parent. Flipping one control's state must recompose
 * ONLY that leaf — never its siblings — which is the property the production wiring relies on. Each leaf
 * wraps the SAME reusable widgets the real screen uses ([LikeButton], [ToggleIconButton],
 * [MorphPlayButton], [WavySeekBar]), so the isolation is demonstrated through the real UI primitives.
 *
 * The test drives the Compose clock manually ([androidx.compose.ui.test.MainTestClock.autoAdvance] =
 * false) because the seek bar's wave and the buttons' animations run forever; a plain `waitForIdle()`
 * would hang on those. Advancing a single frame after a state change is enough to flush the pending
 * recomposition we're measuring, without waiting on the never-idle animations.
 */
class PlayerRecompositionIsolationTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Body-level recomposition counters for every leaf we care about. */
    private class Counters {
        val artwork = mutableIntStateOf(0)
        val title = mutableIntStateOf(0)
        val like = mutableIntStateOf(0)
        val shuffle = mutableIntStateOf(0)
        val repeat = mutableIntStateOf(0)
        val playPause = mutableIntStateOf(0)
        val seekBar = mutableIntStateOf(0)
    }

    /** Mutable state driving each dynamic control, exactly as a collected StateFlow value would. */
    private class Inputs {
        val isLiked = mutableStateOf(false)
        val shuffleEnabled = mutableStateOf(false)
        val repeatMode = mutableStateOf(RepeatMode.OFF)
        val isPlaying = mutableStateOf(false)
    }

    /**
     * The isolated leaf layer. Structurally identical to the real player: static artwork/title regions
     * plus one leaf per dynamic control, each reading ONLY its own [State] and incrementing its counter
     * in its own body.
     */
    @Composable
    private fun IsolatedContent(counters: Counters, inputs: Inputs) {
        Column {
            // "Artwork" + "title": static regions that read NO dynamic state. Must never recompose when
            // a control is toggled.
            CountingLeaf(counters.artwork)
            CountingLeaf(counters.title)

            LikeLeaf(counters.like, inputs.isLiked)
            ShuffleLeaf(counters.shuffle, inputs.shuffleEnabled)
            RepeatLeaf(counters.repeat, inputs.repeatMode)
            PlayPauseLeaf(counters.playPause, inputs.isPlaying)
            SeekBarLeaf(counters.seekBar, inputs.isPlaying)
        }
    }

    @Composable
    private fun CountingLeaf(counter: MutableIntState) {
        counter.intValue++
        // Renders nothing dynamic — a stand-in for the artwork pager / title block.
    }

    @Composable
    private fun LikeLeaf(counter: MutableIntState, isLiked: State<Boolean>) {
        counter.intValue++
        LikeButton(isLiked = isLiked.value, onClick = {})
    }

    @Composable
    private fun ShuffleLeaf(counter: MutableIntState, shuffleEnabled: State<Boolean>) {
        counter.intValue++
        val on by shuffleEnabled
        ToggleIconButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "shuffle",
            active = on,
            onClick = {},
        )
    }

    @Composable
    private fun RepeatLeaf(counter: MutableIntState, repeatMode: State<RepeatMode>) {
        counter.intValue++
        val mode by repeatMode
        ToggleIconButton(
            icon = Icons.Filled.Repeat,
            contentDescription = "repeat",
            active = mode != RepeatMode.OFF,
            onClick = {},
        )
    }

    @Composable
    private fun PlayPauseLeaf(counter: MutableIntState, isPlaying: State<Boolean>) {
        counter.intValue++
        MorphPlayButton(isPlaying = isPlaying.value, onClick = {})
    }

    @Composable
    private fun SeekBarLeaf(counter: MutableIntState, isPlaying: State<Boolean>) {
        counter.intValue++
        WavySeekBar(
            position = { 0L },
            bufferedPosition = { 0L },
            duration = 100_000L,
            isPlaying = isPlaying.value,
            onSeek = {},
            modifier = Modifier.width(300.dp),
        )
    }

    private fun Counters.snapshot(): IntArray = intArrayOf(
        artwork.intValue, title.intValue, like.intValue, shuffle.intValue,
        repeat.intValue, playPause.intValue, seekBar.intValue,
    )

    /**
     * Set the content, flush the initial composition, run [mutate] (a state flip on the UI thread), flush
     * one more frame, and return (baseline, after) counter snapshots. The Compose clock is driven by hand
     * so the forever-running animations in the widgets never stall the test.
     */
    private fun measure(mutate: (Inputs) -> Unit): Pair<IntArray, IntArray> {
        val counters = Counters()
        val inputs = Inputs()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { IsolatedContent(counters, inputs) }
        // Flush the initial composition + first frame.
        composeRule.mainClock.advanceTimeByFrame()
        val base = counters.snapshot()

        composeRule.runOnUiThread { mutate(inputs) }
        // A couple of frames apply the snapshot change and let each invalidated scope recompose (an
        // animated widget may recompose again as its own animation state advances — that's still that
        // leaf and only that leaf).
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        return base to counters.snapshot()
    }

    // Index map into the snapshot arrays.
    private val ARTWORK = 0
    private val TITLE = 1
    private val LIKE = 2
    private val SHUFFLE = 3
    private val REPEAT = 4
    private val PLAY = 5
    private val SEEK = 6

    /**
     * The core isolation assertion: every leaf NOT in [shouldRecompose] must hold its exact baseline
     * count (zero recompositions — this is the property we're proving), and each leaf that IS in
     * [shouldRecompose] must have recomposed at least once. (A toggled leaf may recompose more than once
     * because its own widget animations — the like tint fade, the play-button corner morph, the wave —
     * advance on later frames; that extra recomposition is still confined to that same leaf.)
     */
    private fun assertOnly(base: IntArray, after: IntArray, vararg shouldRecompose: Int) {
        val names = arrayOf("artwork", "title", "like", "shuffle", "repeat", "play/pause", "seek bar")
        val expected = shouldRecompose.toSet()
        for (i in base.indices) {
            if (i in expected) {
                assertTrue(
                    "${names[i]} SHOULD recompose (was ${after[i]}, baseline ${base[i]})",
                    after[i] > base[i]
                )
            } else {
                assertEquals("${names[i]} must NOT recompose", base[i], after[i])
            }
        }
    }

    @Test
    fun togglingLike_recomposesOnlyLike() {
        val (base, after) = measure { it.isLiked.value = true }
        assertOnly(base, after, LIKE)
    }

    @Test
    fun togglingShuffle_recomposesOnlyShuffle() {
        val (base, after) = measure { it.shuffleEnabled.value = true }
        assertOnly(base, after, SHUFFLE)
    }

    @Test
    fun cyclingRepeat_recomposesOnlyRepeat() {
        val (base, after) = measure { it.repeatMode.value = RepeatMode.ALL }
        assertOnly(base, after, REPEAT)
    }

    @Test
    fun togglingPlayPause_recomposesOnlyPlayPauseAndSeekBar() {
        // Play/pause legitimately drives BOTH the morph button and the seek bar's wave animation — those
        // two (and only those two) may recompose. Artwork / title / like / shuffle / repeat must not.
        val (base, after) = measure { it.isPlaying.value = true }
        assertOnly(base, after, PLAY, SEEK)
    }
}
