package tk.glucodata.ui

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Pick readable 1/2/2.5/5 steps using the actual plot and label height. */
internal fun chartYAxisStep(
    span: Float,
    plotHeight: Float,
    labelHeight: Float,
    minimumSpacing: Float,
    isMmol: Boolean
): Float {
    val minimumStep = if (isMmol) 0.5f else 10f
    if (!span.isFinite() || span <= 0f || !plotHeight.isFinite() || plotHeight <= 0f) {
        return minimumStep
    }
    val spacing = maxOf(minimumSpacing, labelHeight * 2.5f, 1f)
    val intervals = (plotHeight / spacing).toInt().coerceIn(2, 10)
    val raw = maxOf(span / intervals, minimumStep).toDouble()
    val magnitude = 10.0.pow(floor(log10(raw)))
    val factor = raw / magnitude
    val nice = when {
        factor <= 1.0 -> 1.0
        factor <= 2.0 -> 2.0
        factor <= 2.5 -> 2.5
        factor <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * magnitude).toFloat()
}

internal fun chartYAxisTicks(low: Float, high: Float, step: Float): List<Float> {
    if (!low.isFinite() || !high.isFinite() || !step.isFinite() || step <= 0f || high <= low) {
        return emptyList()
    }
    val first = ceil(low.toDouble() / step).toLong()
    val last = floor(high.toDouble() / step).toLong()
    if (last < first) return emptyList()
    return (0L..(last - first).coerceAtMost(100L)).map { ((first + it) * step.toDouble()).toFloat() }
}

/** Separate extrema labels while keeping both inside the plot; input is top then bottom. */
internal fun chartExtremaLabelCenters(
    topY: Float,
    bottomY: Float,
    height: Float,
    labelHeight: Float,
    gap: Float
): Pair<Float, Float> {
    val half = (labelHeight / 2f).coerceAtMost(height / 2f)
    val lower = half
    val upper = (height - half).coerceAtLeast(lower)
    var top = topY.coerceIn(lower, upper)
    var bottom = bottomY.coerceIn(lower, upper)
    val separation = (labelHeight + gap).coerceAtMost(upper - lower)
    if (bottom - top < separation) {
        val midpoint = (top + bottom) / 2f
        top = (midpoint - separation / 2f).coerceIn(lower, upper - separation)
        bottom = top + separation
    }
    return top to bottom
}
