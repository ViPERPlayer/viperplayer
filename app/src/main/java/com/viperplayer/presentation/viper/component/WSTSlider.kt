/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viperplayer.presentation.viper.component

import androidx.annotation.IntRange
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastFirst
import androidx.compose.ui.util.lerp
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * <a href="https://m3.material.io/components/sliders/overview" class="external" target="_blank">Material Design slider</a>.
 *
 * Sliders allow users to make selections from a range of values.
 *
 * It uses [SliderDefaults.Thumb] and [SliderDefaults.Track] as the thumb and track.
 *
 * Sliders reflect a range of values along a bar, from which users may select a single value.
 * They are ideal for adjusting settings such as volume, brightness, or applying image filters.
 *
 * ![Sliders image](https://developer.android.com/images/reference/androidx/compose/material3/sliders.png)
 *
 * Use continuous sliders to allow users to make meaningful selections that don’t
 * require a specific value:
 *
 * @sample androidx.compose.material3.samples.SliderSample
 *
 * You can allow the user to choose only between predefined set of values by specifying the amount
 * of steps between min and max values:
 *
 * @sample androidx.compose.material3.samples.StepsSliderSample
 *
 * @param value current value of the slider. If outside of [valueRange] provided, value will be
 * coerced to this range.
 * @param onValueChange callback in which value should be updated
 * @param modifier the [Modifier] to be applied to this slider
 * @param enabled controls the enabled state of this slider. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param valueRange range of values that this slider can take. The passed [value] will be coerced
 * to this range.
 * @param steps if greater than 0, specifies the amount of discrete allowable values, evenly
 * distributed across the whole value range. If 0, the slider will behave continuously and allow any
 * value from the range specified. Must not be negative.
 * @param onValueChangeFinished called when value change has ended. This should not be used to
 * update the slider value (use [onValueChange] instead), but rather to know when the user has
 * completed selecting a new value by ending a drag or a click.
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this slider. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this slider in different states.
 */
@Composable
@ExperimentalMaterial3Api
fun WSTSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    @IntRange(from = 0) steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val onValueChangeFinishedState = rememberUpdatedState(onValueChangeFinished)
    val state = remember(
        steps,
        valueRange
    ) {
        SliderState(
            value,
            steps,
            { onValueChangeFinishedState.value?.invoke() },
            valueRange

        )
    }

    state.onValueChange = onValueChange
    state.value = value

    WSTSlider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb()
        },
        track = { sliderState ->
            SliderDefaults.Track(sliderState)
        }
    )
}

/**
 * <a href="https://m3.material.io/components/sliders/overview" class="external" target="_blank">Material Design slider</a>.
 *
 * Sliders allow users to make selections from a range of values.
 *
 * Sliders reflect a range of values along a bar, from which users may select a single value.
 * They are ideal for adjusting settings such as volume, brightness, or applying image filters.
 *
 * ![Sliders image](https://developer.android.com/images/reference/androidx/compose/material3/sliders.png)
 *
 * Use continuous sliders to allow users to make meaningful selections that don’t
 * require a specific value:
 *
 * @sample androidx.compose.material3.samples.SliderSample
 *
 * You can allow the user to choose only between predefined set of values by specifying the amount
 * of steps between min and max values:
 *
 * @sample androidx.compose.material3.samples.StepsSliderSample
 *
 * Slider using a custom thumb:
 *
 * @sample androidx.compose.material3.samples.SliderWithCustomThumbSample
 *
 * Slider using custom track and thumb:
 *
 * @sample androidx.compose.material3.samples.SliderWithCustomTrackAndThumb
 *
 * @param state [SliderState] which contains the slider's current value.
 * @param modifier the [Modifier] to be applied to this slider
 * @param enabled controls the enabled state of this slider. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this slider. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this slider in different states.
 * @param thumb the thumb to be displayed on the slider, it is placed on top of the track. The
 * lambda receives a [SliderState] which is used to obtain the current active track.
 * @param track the track to be displayed on the slider, it is placed underneath the thumb. The
 * lambda receives a [SliderState] which is used to obtain the current active track.
 */
@Composable
@ExperimentalMaterial3Api
fun WSTSlider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = {
        SliderDefaults.Thumb()
    },
    track: @Composable (SliderState) -> Unit = { sliderState ->
        SliderDefaults.Track(sliderState)
    }
) {
    require(state.steps >= 0) { "steps should be >= 0" }

    WSTSliderImpl(
        state = state,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WSTSliderImpl(
    modifier: Modifier,
    state: SliderState,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    thumb: @Composable (SliderState) -> Unit,
    track: @Composable (SliderState) -> Unit
) {
    state.isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val press = Modifier.sliderTapModifier(
        state,
        interactionSource,
        enabled
    )
    val drag = Modifier.draggable(
        orientation = Orientation.Horizontal,
        reverseDirection = state.isRtl,
        enabled = enabled,
        interactionSource = interactionSource,
        onDragStopped = { state.gestureEndAction() },
        startDragImmediately = state.isDragging,
        state = state
    )

    Layout(
        {
            Box(modifier = Modifier.layoutId(SliderComponents.THUMB)) {
                thumb(state)
            }
            Box(modifier = Modifier.layoutId(SliderComponents.TRACK)) {
                track(state)
            }
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .sliderSemantics(
                state,
                enabled
            )
            .focusable(enabled, interactionSource)
            .then(press)
            .then(drag)
    ) { measurables, constraints ->

        val thumbPlaceable = measurables.fastFirst {
            it.layoutId == SliderComponents.THUMB
        }.measure(constraints)

        val trackPlaceable = measurables.fastFirst {
            it.layoutId == SliderComponents.TRACK
        }.measure(
            constraints.offset(
                horizontal = -thumbPlaceable.width
            ).copy(minHeight = 0)
        )

        val sliderWidth = thumbPlaceable.width + trackPlaceable.width
        val sliderHeight = max(trackPlaceable.height, thumbPlaceable.height)

        state.updateDimensions(
            thumbPlaceable.width.toFloat(),
            sliderWidth
        )

        val trackOffsetX = thumbPlaceable.width / 2
        val thumbOffsetX = ((trackPlaceable.width) * state.coercedValueAsFraction).roundToInt()
        val trackOffsetY = (sliderHeight - trackPlaceable.height) / 2
        val thumbOffsetY = (sliderHeight - thumbPlaceable.height) / 2

        layout(sliderWidth, sliderHeight) {
            trackPlaceable.placeRelative(
                trackOffsetX,
                trackOffsetY
            )
            thumbPlaceable.placeRelative(
                thumbOffsetX,
                thumbOffsetY
            )
        }
    }
}

/**
 * Object to hold defaults used by [Slider]
 */
@Stable
object SliderDefaults {
    /**
     * The Default thumb for [Slider] and [RangeSlider]
     *
     * @param interactionSource the [MutableInteractionSource] representing the stream of
     * [Interaction]s for this thumb. You can create and pass in your own `remember`ed
     * instance to observe
     * @param modifier the [Modifier] to be applied to the thumb.
     * @param enabled controls the enabled state of this slider. When `false`, this component will
     * not respond to user input, and it will appear visually disabled and disabled to
     * accessibility services.
     */
    @Composable
    fun Thumb() {
        Box(
            modifier = Modifier
                .size(
                    width = 6.dp,
                    height = 24.dp
                )
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }

    /**
     * The Default track for [Slider]
     *
     * @param sliderState [SliderState] which is used to obtain the current active track.
     * @param modifier the [Modifier] to be applied to the track.
     * @param enabled controls the enabled state of this slider. When `false`, this component will
     * not respond to user input, and it will appear visually disabled and disabled to
     * accessibility services.
     */
    @Composable
    @ExperimentalMaterial3Api
    fun Track(
        sliderState: SliderState,
    ) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        val leftWidth by remember {
            derivedStateOf {
                size.width * sliderState.coercedValueAsFraction
            }
        }
        val rightWidth by remember {
            derivedStateOf {
                size.width * (1f - sliderState.coercedValueAsFraction)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { newSize -> size = newSize }
        ) {
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { leftWidth.toDp() } - 9.dp)
                    .height(24.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 2.dp,
                            bottomEnd = 2.dp,
                            bottomStart = 16.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.CenterStart)
            )
            Box(
                modifier = Modifier
                    .width(with(LocalDensity.current) { rightWidth.toDp() } - 9.dp)
                    .height(24.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 2.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 2.dp
                        )
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .align(Alignment.CenterEnd)
            )
        }
    }
}

private fun snapValueToTick(
    current: Float,
    tickFractions: FloatArray,
    minPx: Float,
    maxPx: Float
): Float {
    // target is a closest anchor to the `current`, if exists
    return tickFractions
        .minByOrNull { abs(lerp(minPx, maxPx, it) - current) }
        ?.run { lerp(minPx, maxPx, this) }
        ?: current
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))

// Scale x.start, x.endInclusive from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x: SliderRange, a2: Float, b2: Float) =
    SliderRange(scale(a1, b1, x.start, a2, b2), scale(a1, b1, x.endInclusive, a2, b2))

// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.sliderSemantics(
    state: SliderState,
    enabled: Boolean
): Modifier {
    return semantics {
        if (!enabled) disabled()
        setProgress(
            action = { targetValue ->
                var newValue = targetValue.coerceIn(
                    state.valueRange.start,
                    state.valueRange.endInclusive
                )
                val originalVal = newValue
                val resolvedValue = if (state.steps > 0) {
                    var distance: Float = newValue
                    for (i in 0..state.steps + 1) {
                        val stepValue = lerp(
                            state.valueRange.start,
                            state.valueRange.endInclusive,
                            i.toFloat() / (state.steps + 1)
                        )
                        if (abs(stepValue - originalVal) <= distance) {
                            distance = abs(stepValue - originalVal)
                            newValue = stepValue
                        }
                    }
                    newValue
                } else {
                    newValue
                }

                // This is to keep it consistent with AbsSeekbar.java: return false if no
                // change from current.
                if (resolvedValue == state.value) {
                    false
                } else {
                    if (resolvedValue != state.value) {
                        if (state.onValueChange != null) {
                            state.onValueChange?.let {
                                it(resolvedValue)
                            }
                        } else {
                            state.value = resolvedValue
                        }
                    }
                    state.onValueChangeFinished?.invoke()
                    true
                }
            }
        )
    }.progressSemantics(
        state.value,
        state.valueRange.start..state.valueRange.endInclusive,
        state.steps
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Stable
private fun Modifier.sliderTapModifier(
    state: SliderState,
    interactionSource: MutableInteractionSource,
    enabled: Boolean
) = if (enabled) {
    pointerInput(state, interactionSource) {
        detectTapGestures(
            onPress = { state.onPress(it) },
            onTap = {
                state.dispatchRawDelta(0f)
                state.gestureEndAction()
            }
        )
    }
} else {
    this
}

private enum class SliderComponents {
    THUMB,
    TRACK
}

/**
 * Class that holds information about [Slider]'s and [RangeSlider]'s active track
 * and fractional positions where the discrete ticks should be drawn on the track.
 */
@Suppress("DEPRECATION")
@Deprecated("Not necessary with the introduction of Slider state")
@Stable
class SliderPositions(
    initialActiveRange: ClosedFloatingPointRange<Float> = 0f..1f,
    initialTickFractions: FloatArray = floatArrayOf()
) {
    /**
     * [ClosedFloatingPointRange] that indicates the current active range for the
     * start to thumb for a [Slider] and start thumb to end thumb for a [RangeSlider].
     */
    var activeRange: ClosedFloatingPointRange<Float> by mutableStateOf(initialActiveRange)
        internal set

    /**
     * The discrete points where a tick should be drawn on the track.
     * Each value of tickFractions should be within the range [0f, 1f]. If
     * the track is continuous, then tickFractions will be an empty [FloatArray].
     */
    var tickFractions: FloatArray by mutableStateOf(initialTickFractions)
        internal set

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SliderPositions) return false

        if (activeRange != other.activeRange) return false
        if (!tickFractions.contentEquals(other.tickFractions)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = activeRange.hashCode()
        result = 31 * result + tickFractions.contentHashCode()
        return result
    }
}

/**
 * Class that holds information about [Slider]'s active range.
 *
 * @param value [Float] that indicates the initial
 * position of the thumb. If outside of [valueRange]
 * provided, value will be coerced to this range.
 * @param steps if greater than 0, specifies the amounts of discrete values, evenly distributed
 * between across the whole value range. If 0, range slider will behave as a continuous slider and
 * allow to choose any value from the range specified. Must not be negative.
 * @param onValueChangeFinished lambda to be invoked when value change has ended. This callback
 * shouldn't be used to update the range slider values (use [onValueChange] for that),
 * but rather to know when the user has completed selecting a new value by ending a drag or a click.
 * @param valueRange range of values that Slider values can take. [value] will be
 * coerced to this range.
 */
@Stable
@ExperimentalMaterial3Api
class SliderState(
    value: Float = 0f,
    @param:IntRange(from = 0)
    val steps: Int = 0,
    val onValueChangeFinished: (() -> Unit)? = null,
    val valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) : DraggableState {

    private var valueState by mutableFloatStateOf(value)

    /**
     * [Float] that indicates the current value that the thumb
     * currently is in respect to the track.
     */
    var value: Float
        set(newVal) {
            val coercedValue = newVal.coerceIn(valueRange.start, valueRange.endInclusive)
            val snappedValue = snapValueToTick(
                coercedValue,
                tickFractions,
                valueRange.start,
                valueRange.endInclusive
            )
            valueState = snappedValue
        }
        get() = valueState

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend DragScope.() -> Unit
    ): Unit = coroutineScope {
        isDragging = true
        scrollMutex.mutateWith(dragScope, dragPriority, block)
        isDragging = false
    }

    override fun dispatchRawDelta(delta: Float) {
        val maxPx = max(totalWidth - thumbWidth / 2, 0f)
        val minPx = min(thumbWidth / 2, maxPx)
        rawOffset = (rawOffset + delta + pressOffset)
        pressOffset = 0f
        val offsetInTrack = snapValueToTick(rawOffset, tickFractions, minPx, maxPx)
        val scaledUserValue = scaleToUserValue(minPx, maxPx, offsetInTrack)
        if (scaledUserValue != this.value) {
            if (onValueChange != null) {
                onValueChange?.let { it(scaledUserValue) }
            } else {
                this.value = scaledUserValue
            }
        }
    }

    /**
     * callback in which value should be updated
     */
    internal var onValueChange: ((Float) -> Unit)? = null

    internal val tickFractions = stepsToTickFractions(steps)
    private var totalWidth by mutableIntStateOf(0)
    internal var isRtl = false
    private var thumbWidth by mutableFloatStateOf(0f)

    internal val coercedValueAsFraction
        get() = calcFraction(
            valueRange.start,
            valueRange.endInclusive,
            value.coerceIn(valueRange.start, valueRange.endInclusive)
        )

    internal var isDragging by mutableStateOf(false)
        private set

    internal fun updateDimensions(
        newThumbWidth: Float,
        newTotalWidth: Int
    ) {
        thumbWidth = newThumbWidth
        totalWidth = newTotalWidth
    }

    internal val gestureEndAction = {
        if (!isDragging) {
            // check isDragging in case the change is still in progress (touch -> drag case)
            onValueChangeFinished?.invoke()
        }
    }

    internal fun onPress(pos: Offset) {
        val to = if (isRtl) totalWidth - pos.x else pos.x
        pressOffset = to - rawOffset
    }

    private var rawOffset by mutableFloatStateOf(scaleToOffset(0f, 0f, value))
    private var pressOffset by mutableFloatStateOf(0f)
    private val dragScope: DragScope = object : DragScope {
        override fun dragBy(pixels: Float): Unit = dispatchRawDelta(pixels)
    }

    private val scrollMutex = MutatorMutex()

    private fun scaleToUserValue(minPx: Float, maxPx: Float, offset: Float) =
        scale(minPx, maxPx, offset, valueRange.start, valueRange.endInclusive)

    private fun scaleToOffset(minPx: Float, maxPx: Float, userValue: Float) =
        scale(valueRange.start, valueRange.endInclusive, userValue, minPx, maxPx)
}

/**
 * Immutable float range for [RangeSlider]
 *
 * Used in [RangeSlider] to determine the active track range for the component.
 * The range is as follows: SliderRange.start..SliderRange.endInclusive.
 */
@Immutable
@JvmInline
internal value class SliderRange(
    val packedValue: Long
) {
    /**
     * start of the [SliderRange]
     */
    @Stable
    val start: Float
        get() {
            // Explicitly compare against packed values to avoid auto-boxing of Size.Unspecified
            check(this.packedValue != Unspecified.packedValue) {
                "SliderRange is unspecified"
            }
            return unpackFloat1(packedValue)
        }

    /**
     * End (inclusive) of the [SliderRange]
     */
    @Stable
    val endInclusive: Float
        get() {
            // Explicitly compare against packed values to avoid auto-boxing of Size.Unspecified
            check(this.packedValue != Unspecified.packedValue) {
                "SliderRange is unspecified"
            }
            return unpackFloat2(packedValue)
        }

    companion object {
        /**
         * Represents an unspecified [SliderRange] value, usually a replacement for `null`
         * when a primitive value is desired.
         */
        @Stable
        val Unspecified = SliderRange(Float.NaN, Float.NaN)
    }

    /**
     * String representation of the [SliderRange]
     */
    override fun toString() = if (isSpecified) {
        "$start..$endInclusive"
    } else {
        "FloatRange.Unspecified"
    }
}

/**
 * Creates a [SliderRange] from a given start and endInclusive float.
 * It requires endInclusive to be >= start.
 *
 * @param start float that indicates the start of the range
 * @param endInclusive float that indicates the end of the range
 */
@Stable
internal fun SliderRange(start: Float, endInclusive: Float): SliderRange {
    val isUnspecified = start.isNaN() && endInclusive.isNaN()
    require(isUnspecified || start <= endInclusive) {
        "start($start) must be <= endInclusive($endInclusive)"
    }
    return SliderRange(packFloats(start, endInclusive))
}

/**
 * Creates a [SliderRange] from a given [ClosedFloatingPointRange].
 * It requires range.endInclusive >= range.start.
 *
 * @param range the ClosedFloatingPointRange<Float> for the range.
 */
@Stable
internal fun SliderRange(range: ClosedFloatingPointRange<Float>): SliderRange {
    val start = range.start
    val endInclusive = range.endInclusive
    val isUnspecified = start.isNaN() && endInclusive.isNaN()
    require(isUnspecified || start <= endInclusive) {
        "ClosedFloatingPointRange<Float>.start($start) must be <= " +
                "ClosedFloatingPoint.endInclusive($endInclusive)"
    }
    return SliderRange(packFloats(start, endInclusive))
}

/**
 * Check for if a given [SliderRange] is not [SliderRange.Unspecified].
 */
@Stable
internal val SliderRange.isSpecified: Boolean
    get() =
        packedValue != SliderRange.Unspecified.packedValue
