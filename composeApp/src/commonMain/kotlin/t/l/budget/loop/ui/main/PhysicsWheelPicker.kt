package t.l.budget.loop.ui.main

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import t.l.budget.loop.platform.rememberHapticFeedbackManager

@Composable
fun PhysicsWheelPicker(
    range: IntRange,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 44.dp,
    visibleItemsCount: Int = 5,
    textStyle: TextStyle,
    label: (Int) -> String = { it.toString() },
) {
    val density = LocalDensity.current
    val adjustedVisibleCount = max(3, if (visibleItemsCount % 2 == 0) visibleItemsCount + 1 else visibleItemsCount)
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val minIndex = range.first
    val maxIndex = range.last
    val clampedValue = value.coerceIn(range)
    val minPx = minIndex * itemHeightPx
    val maxPx = maxIndex * itemHeightPx
    val resistancePx = itemHeightPx * 1.6f
    val onValueChangeState by rememberUpdatedState(onValueChange)
    val haptics = rememberHapticFeedbackManager()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    var positionPx by remember(minIndex, maxIndex, itemHeightPx) {
        mutableFloatStateOf(clampedValue * itemHeightPx)
    }

    val selectedIndex by remember {
        derivedStateOf {
            if (itemHeightPx == 0f) {
                minIndex
            } else {
                (positionPx / itemHeightPx).roundToInt().coerceIn(minIndex, maxIndex)
            }
        }
    }
    val centerIndex by remember {
        derivedStateOf {
            if (itemHeightPx == 0f) minIndex else (positionPx / itemHeightPx).roundToInt()
        }
    }
    val visibleRange by remember {
        derivedStateOf {
            val half = adjustedVisibleCount / 2
            val start = (centerIndex - half - 2).coerceAtLeast(minIndex)
            val end = (centerIndex + half + 2).coerceAtMost(maxIndex)
            start..end
        }
    }

    LaunchedEffect(clampedValue, itemHeightPx, minIndex, maxIndex, isDragged, selectedIndex) {
        if (isDragged || clampedValue == selectedIndex) return@LaunchedEffect
        val target = clampedValue * itemHeightPx
        if (abs(positionPx - target) < 0.5f) {
            positionPx = target
            return@LaunchedEffect
        }
        AnimationState(positionPx).animateTo(
            target,
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        ) {
            positionPx = this.value
        }
    }

    var hapticsArmed by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        onValueChangeState(selectedIndex)
        if (hapticsArmed) {
            haptics.tick()
        } else {
            hapticsArmed = true
        }
    }

    val scrollableState = rememberScrollableState { delta ->
        val next = positionPx - delta
        positionPx = applyRubberBand(next, minPx, maxPx, resistancePx)
        delta
    }
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = 4.6f) }
    val flingBehavior = remember(decay, itemHeightPx, minPx, maxPx) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                var lastValue = 0f
                AnimationState(0f, initialVelocity).animateDecay(decay) {
                    val delta = this.value - lastValue
                    lastValue = this.value
                    scrollBy(delta)
                }
                val targetIndex = (positionPx / itemHeightPx).roundToInt().coerceIn(minIndex, maxIndex)
                val target = targetIndex * itemHeightPx
                AnimationState(positionPx).animateTo(
                    target,
                    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                ) {
                    positionPx = this.value
                }
                return 0f
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeight * adjustedVisibleCount)
            .clipToBounds()
            .scrollable(
                orientation = Orientation.Vertical,
                state = scrollableState,
                flingBehavior = flingBehavior,
                interactionSource = interactionSource,
            ),
    ) {
        val viewportHeightPx = itemHeightPx * adjustedVisibleCount
        val centerY = viewportHeightPx / 2f
        val cameraDistance = density.density * 16f

        visibleRange.forEach { index ->
            val itemOffsetPx = (index * itemHeightPx) - positionPx
            val topPx = centerY + itemOffsetPx - (itemHeightPx / 2f)
            val normalized = normalizedOffset(itemOffsetPx, itemHeightPx, adjustedVisibleCount)
            val rotation = rotationXForOffset(normalized)
            val alpha = gaussianAlpha(normalized)
            val scale = scaleForOffset(normalized)

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, topPx.roundToInt()) }
                    .height(itemHeight)
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationX = rotation
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        this.cameraDistance = cameraDistance
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = label(index),
                    style = textStyle,
                )
            }
        }
    }
}
