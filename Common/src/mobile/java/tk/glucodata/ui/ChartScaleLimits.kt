package tk.glucodata.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import tk.glucodata.ui.util.GlucoseFormatter

internal const val CHART_SCALE_LOW_KEY = "dashboard_chart_scale_limit_low_mgdl"
internal const val CHART_SCALE_HIGH_KEY = "dashboard_chart_scale_limit_high_mgdl"

internal data class ChartScaleLimits(val low: Float, val high: Float, val minimumSpan: Float) {
    fun constrain(min: Float, max: Float): Pair<Float, Float> {
        val boundedMin = (min.takeIf { it.isFinite() } ?: low).coerceIn(low, high - minimumSpan)
        val boundedMax = (max.takeIf { it.isFinite() } ?: high)
            .coerceIn(boundedMin + minimumSpan, high)
        return boundedMin to boundedMax
    }
}

@Composable
internal fun rememberChartScaleLimits(isMmol: Boolean): ChartScaleLimits {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
    }
    fun read(): ChartScaleLimits {
        val storedLow = prefs.getFloat(CHART_SCALE_LOW_KEY, 0f)
        val storedHigh = prefs.getFloat(CHART_SCALE_HIGH_KEY, 540f)
        val low = (storedLow.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 216f)
        val high = (storedHigh.takeIf { it.isFinite() } ?: 540f).coerceIn(low + 108f, 1000f)
        val factor = if (isMmol) GlucoseFormatter.mmolToMg(1f) else 1f
        return ChartScaleLimits(low / factor, high / factor, 108f / factor)
    }
    var limits by remember(prefs, isMmol) { mutableStateOf(read()) }
    DisposableEffect(prefs, isMmol) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == CHART_SCALE_LOW_KEY || key == CHART_SCALE_HIGH_KEY) limits = read()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        limits = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return limits
}

internal fun saveChartScaleLimits(context: Context, low: Float, high: Float, isMmol: Boolean) {
    val factor = if (isMmol) GlucoseFormatter.mmolToMg(1f) else 1f
    val lower = (low * factor).coerceIn(0f, 216f)
    val upper = (high * factor).coerceIn(lower + 108f, 1000f)
    context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        .edit().putFloat(CHART_SCALE_LOW_KEY, lower).putFloat(CHART_SCALE_HIGH_KEY, upper).apply()
}
