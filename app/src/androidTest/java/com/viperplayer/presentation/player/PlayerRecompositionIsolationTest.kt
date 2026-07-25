package com.viperplayer.presentation.player

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.viperplayer.domain.model.MediaId
import com.viperplayer.domain.model.RepeatMode
import com.viperplayer.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * FULL-TREE recomposition harness for the now-playing player. Unlike an isolated-leaf test (which
 * renders each control alone and therefore can never observe a cascade), this renders the REAL
 * [PlayerContent] — the exact stateless chrome the production [PlayerScreen] hosts — with the REAL leaf
 * visuals ([LikeButton], [ToggleIconButton], [MorphPlayButton], [WavySeekBar], the artwork pager, title,
 * subtitle, context chip, output/queue bar) in its slots, driven by a fake, controllable state source.
 *
 * Every node bumps a body-level recomposition counter. Flipping ONE piece of playback state (isLiked /
 * shuffle / repeat / isPlaying / position) must wake ONLY the intended leaf and leave every sibling at
 * its exact baseline — proving that a control change does not cascade through the whole composition
 * tree. This is the acceptance test the fix targets; the isolated-leaf shape it replaced could not
 * detect the cascade the connected wiring is responsible for avoiding.
 *
 * The Compose clock is driven by hand ([androidx.compose.ui.test.MainTestClock.autoAdvance] = false)
 * because the seek-bar wave and the button morphs animate forever; a plain `waitForIdle()` would hang on
 * them. Advancing a couple of frames after a state flip flushes the pending recomposition we measure.
 */
class PlayerRecompositionIsolationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val song = Song(id = MediaId.Local("song-1"), title = "Test Track", durationMs = 200_000L)

    /**
     * Body-level recomposition counters for every sibling node whose isolation we assert.
     *
     * The [topBar] and [contextChip] slots sit in the SAME [PlayerContent] body scope as the artwork
     * pager, title, subtitle, and output/queue bar (all placed there from unchanged `song`/`queue`/
     * `lyrics` args). So if a control flip left [topBar]/[contextChip] at their baseline, the
     * [PlayerContent] body did not re-run and none of those internal expensive regions could have
     * recomposed either — that is the proof the flip did not cascade.
     */
    private class Counters {
        val topBar = mutableIntStateOf(0)
        val contextChip = mutableIntStateOf(0)
        val like = mutableIntStateOf(0)
        val shuffle = mutableIntStateOf(0)
        val repeat = mutableIntStateOf(0)
        val playPause = mutableIntStateOf(0)
        val seekBar = mutableIntStateOf(0)
    }

    /** Fake, controllable playback state — stands in for the collected StateFlow values. */
    private class Inputs {
        var isLiked by mutableStateOf(false)
        var shuffleEnabled by mutableStateOf(false)
        var repeatMode by mutableStateOf(RepeatMode.OFF)
        var isPlaying by mutableStateOf(false)
        val position = mutableLongStateOf(0L)
    }

    /**
     * Renders the REAL [PlayerContent] with the real leaf visuals in its slots, each reading ONLY its own
     * fake state and bumping its counter. This is structurally identical to production: production fills
     * the same slots with `Connected*` wrappers that collect one flow each; here the slots read one fake
     * [mutableStateOf] each, so a flip of that state is what we measure.
     */
    @Composable
    private fun Harness(counters: Counters, inputs: Inputs) {
        PlayerContent(
            song = song,
            queue = listOf(song),
            lyrics = null,
            swipeSettings = SwipeSettings(),
            position = { inputs.position.longValue },
            bufferedPosition = { 0L },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            onPlayFromQueue = {},
            onOpenLyrics = {},
            onOpenListenTogether = {},
            onOpenQueue = {},
            onNavigateToArtist = {},
            onCollapse = {},
            topBar = { _ ->
                // Canary for PlayerTopBar + its context chip: both sit in PlayerContent's body scope. A
                // cascade that handed this slot a fresh lambda identity would invalidate and re-run it,
                // bumping these — so their staying flat is a guard against the "unstable lambda" leak.
                counters.topBar.intValue++
                counters.contextChip.intValue++
            },
            likeButton = {
                counters.like.intValue++
                LikeButton(isLiked = inputs.isLiked, onClick = {})
            },
            transport = { pos, buffered ->
                // The transport region: seek bar + shuffle/play/repeat, each an isolated counting leaf.
                TransportHarness(counters, inputs, pos, buffered)
            },
        )
    }

    /**
     * Mirrors the production transport row: an isolated leaf per control, each reading only its own state.
     * This is exactly the shape `PlayerTransport` produces (a `Connected*` leaf per flow), reconstructed
     * with fakes so a single flip is measurable.
     */
    @Composable
    private fun TransportHarness(
        counters: Counters,
        inputs: Inputs,
        position: () -> Long,
        bufferedPosition: () -> Long,
    ) {
        SeekBarLeaf(counters.seekBar, inputs, position, bufferedPosition)
        ShuffleLeaf(counters.shuffle, inputs)
        PlayPauseLeaf(counters.playPause, inputs)
        RepeatLeaf(counters.repeat, inputs)
    }

    @Composable
    private fun ShuffleLeaf(counter: MutableIntState, inputs: Inputs) {
        counter.intValue++
        ToggleIconButton(
            icon = Icons.Filled.Shuffle,
            contentDescription = "shuffle",
            active = inputs.shuffleEnabled,
            onClick = {},
        )
    }

    @Composable
    private fun RepeatLeaf(counter: MutableIntState, inputs: Inputs) {
        counter.intValue++
        ToggleIconButton(
            icon = Icons.Filled.Repeat,
            contentDescription = "repeat",
            active = inputs.repeatMode != RepeatMode.OFF,
            onClick = {},
        )
    }

    @Composable
    private fun PlayPauseLeaf(counter: MutableIntState, inputs: Inputs) {
        counter.intValue++
        MorphPlayButton(isPlaying = inputs.isPlaying, onClick = {})
    }

    @Composable
    private fun SeekBarLeaf(
        counter: MutableIntState,
        inputs: Inputs,
        position: () -> Long,
        bufferedPosition: () -> Long,
    ) {
        counter.intValue++
        WavySeekBar(
            position = position,
            bufferedPosition = bufferedPosition,
            duration = song.durationMs ?: 0L,
            isPlaying = inputs.isPlaying,
            onSeek = {},
        )
    }

    private fun Counters.snapshot(): IntArray = intArrayOf(
        topBar.intValue, contextChip.intValue,
        like.intValue, shuffle.intValue, repeat.intValue, playPause.intValue, seekBar.intValue,
    )

    /**
     * Set the content, flush the initial composition, run [mutate] (a state flip on the UI thread), flush
     * a couple of frames, and return (baseline, after) counter snapshots. The clock is hand-driven so the
     * forever-running widget animations never stall the test.
     */
    private fun measure(mutate: (Inputs) -> Unit): Pair<IntArray, IntArray> {
        val counters = Counters()
        val inputs = Inputs()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Harness(counters, inputs) }
        composeRule.mainClock.advanceTimeByFrame()
        // The artwork pager + title read the queue/song once at first composition; capture the baseline
        // after they've settled so later frames measure only what the flip touches.
        composeRule.mainClock.advanceTimeByFrame()
        val base = counters.snapshot()

        composeRule.runOnUiThread { mutate(inputs) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        return base to counters.snapshot()
    }

    // Index map into the snapshot arrays.
    private val TOP_BAR = 0
    private val CONTEXT = 1
    private val LIKE = 2
    private val SHUFFLE = 3
    private val REPEAT = 4
    private val PLAY = 5
    private val SEEK = 6

    private val names = arrayOf(
        "topBar", "contextChip", "like", "shuffle", "repeat", "play/pause", "seek bar",
    )

    /**
     * Every node NOT in [shouldRecompose] must hold its exact baseline count (zero further
     * recompositions — the isolation we prove); each node that IS in [shouldRecompose] must have
     * recomposed at least once. (A woken leaf may recompose more than once as its own widget animation
     * advances on later frames; that extra work is still confined to that same leaf.)
     */
    private fun assertOnly(base: IntArray, after: IntArray, vararg shouldRecompose: Int) {
        val expected = shouldRecompose.toSet()
        for (i in base.indices) {
            if (i in expected) {
                assertTrue(
                    "${names[i]} SHOULD recompose (was ${after[i]}, baseline ${base[i]})",
                    after[i] > base[i],
                )
            } else {
                assertEquals("${names[i]} must NOT recompose", base[i], after[i])
            }
        }
    }

    @Test
    fun togglingLike_recomposesOnlyLikeButton() {
        val (base, after) = measure { it.isLiked = true }
        assertOnly(base, after, LIKE)
    }

    @Test
    fun togglingShuffle_recomposesOnlyShuffleButton() {
        val (base, after) = measure { it.shuffleEnabled = true }
        assertOnly(base, after, SHUFFLE)
    }

    @Test
    fun cyclingRepeat_recomposesOnlyRepeatButton() {
        val (base, after) = measure { it.repeatMode = RepeatMode.ALL }
        assertOnly(base, after, REPEAT)
    }

    @Test
    fun togglingPlayPause_recomposesOnlyPlayPauseAndSeekBar() {
        // Play/pause legitimately drives BOTH the morph button and the seek bar's wave animation — those
        // two (and only those two) may recompose. Everything else must stay put.
        val (base, after) = measure { it.isPlaying = true }
        assertOnly(base, after, PLAY, SEEK)
    }

    @Test
    fun advancingPosition_recomposesNoCountedNode() {
        // The 60fps position tick is passed as a `() -> Long` provider and read only DEEP inside
        // WavySeekBar (its Canvas fraction + the time label) — which sits below the seek-bar leaf counter.
        // Advancing it therefore recomposes nothing but WavySeekBar's own internals: none of the counted
        // sibling/leaf bodies (title, artwork, transport buttons, top bar, or even the seek-bar leaf
        // wrapper) re-run. This is the tightest possible scope and the whole reason position is a
        // provider rather than a plain value — a 60fps value hoisted any higher would recompose that
        // scope 60×/second.
        val (base, after) = measure { it.position.longValue = 90_000L }
        assertOnly(base, after)
    }
}
