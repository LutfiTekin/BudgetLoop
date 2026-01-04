package t.l.budget.loop.ui.main

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.sign

internal fun normalizedOffset(
    itemOffsetPx: Float,
    itemHeightPx: Float,
    visibleItemsCount: Int,
): Float {
    val halfViewportPx = itemHeightPx * (visibleItemsCount / 2f)
    if (halfViewportPx == 0f) return 0f
    return (itemOffsetPx / halfViewportPx).coerceIn(-1f, 1f)
}

internal fun rotationXForOffset(normalizedOffset: Float): Float {
    val magnitude = abs(normalizedOffset).coerceIn(0f, 1f)
    val angleRad = acos((1f - magnitude).toDouble())
    val angleDeg = (angleRad * (180.0 / PI)).toFloat()
    return angleDeg * sign(normalizedOffset)
}

internal fun gaussianAlpha(
    normalizedOffset: Float,
    minAlpha: Float = 0.2f,
    sigma: Float = 0.55f,
): Float {
    val ratio = (normalizedOffset / sigma).toDouble()
    val value = exp(-0.5 * ratio * ratio).toFloat()
    return (minAlpha + (1f - minAlpha) * value).coerceIn(minAlpha, 1f)
}

internal fun scaleForOffset(
    normalizedOffset: Float,
    centerScale: Float = 1.3f,
    edgeScale: Float = 0.8f,
): Float {
    val t = abs(normalizedOffset).coerceIn(0f, 1f)
    return lerpFloat(centerScale, edgeScale, t)
}

internal fun applyRubberBand(
    valuePx: Float,
    minPx: Float,
    maxPx: Float,
    resistancePx: Float,
): Float {
    val clamped = valuePx.coerceIn(minPx, maxPx)
    val overscroll = valuePx - clamped
    if (overscroll == 0f) return valuePx
    val resisted = resistancePx * (1f - exp((-abs(overscroll) / resistancePx).toDouble()).toFloat())
    return clamped + sign(overscroll) * resisted
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}
