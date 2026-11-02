package tk.glucodata.ui

import android.content.Context
import android.content.SharedPreferences
import tk.glucodata.ui.util.GlucoseFormatter

internal fun chartScalePreferences(context: Context): SharedPreferences =
    context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)

internal fun readChartScaleLimits(prefs: SharedPreferences, isMmol: Boolean): ChartScaleLimits {
    val storedLow = prefs.getFloat(CHART_SCALE_LOW_KEY, 0f)
    val storedHigh = prefs.getFloat(CHART_SCALE_HIGH_KEY, 540f)
    val low = (storedLow.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 216f)
    val high = (storedHigh.takeIf { it.isFinite() } ?: 540f).coerceIn(low + 108f, 1000f)
    val factor = if (isMmol) GlucoseFormatter.mmolToMg(1f) else 1f
    return ChartScaleLimits(low / factor, high / factor, 108f / factor)
}

internal fun saveChartScaleLimits(context: Context, low: Float, high: Float, isMmol: Boolean) {
    val factor = if (isMmol) GlucoseFormatter.mmolToMg(1f) else 1f
    val lower = ((low * factor).takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 216f)
    val upper = ((high * factor).takeIf { it.isFinite() } ?: 540f).coerceIn(lower + 108f, 1000f)
    context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        .edit().putFloat(CHART_SCALE_LOW_KEY, lower).putFloat(CHART_SCALE_HIGH_KEY, upper).apply()
}
