package tk.glucodata.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import tk.glucodata.settings.observeChanges

internal const val CHART_SCALE_LOW_KEY = "dashboard_chart_scale_limit_low_mgdl"
internal const val CHART_SCALE_HIGH_KEY = "dashboard_chart_scale_limit_high_mgdl"

internal data class ChartScaleLimits(val low: Float, val high: Float, val minimumSpan: Float) {
    fun constrain(min: Float, max: Float): Pair<Float, Float> {
        val boundedMin = (min.takeIf { it.isFinite() } ?: low).coerceIn(low, maxOf(low, high - minimumSpan))
        val boundedMax = (max.takeIf { it.isFinite() } ?: high)
            .coerceIn(minOf(high, boundedMin + minimumSpan), high)
        return boundedMin to boundedMax
    }
}

@Composable
internal fun rememberChartScaleLimits(isMmol: Boolean): ChartScaleLimits {
    val context = LocalContext.current
    val prefs = remember(context) { chartScalePreferences(context) }
    fun read(): ChartScaleLimits = readChartScaleLimits(prefs, isMmol)
    var limits by remember(prefs, isMmol) { mutableStateOf(read()) }
    DisposableEffect(prefs, isMmol) {
        val stop = prefs.observeChanges(CHART_SCALE_LOW_KEY, CHART_SCALE_HIGH_KEY) {
            limits = read()
        }
        onDispose(stop)
    }
    return limits
}
