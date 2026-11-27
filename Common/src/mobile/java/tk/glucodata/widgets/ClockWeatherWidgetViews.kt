package tk.glucodata.widgets

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import tk.glucodata.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared two-row header for the clock/weather and chart widgets. */
object ClockWeatherWidgetViews {
    private val clockIds = intArrayOf(
        R.id.home_widget_clock_300, R.id.home_widget_clock_400, R.id.home_widget_clock_500,
        R.id.home_widget_clock_600, R.id.home_widget_clock_700
    )

    fun create(
        context: Context,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        w: Float,
        h: Float,
        pxPerDp: Float,
        weather: WidgetWeather?,
        includeBackground: Boolean,
        valueText: String = snapshot.valueText,
        reserveTrendArrowSpace: Boolean = false
    ): RemoteViews {
        val nativeClock = config.fontFamily == WidgetFontFamily.SYSTEM
        val renderer = WidgetRenderer(context)
        val bitmap = renderer.renderClockHeader(
            config, snapshot, w, h, pxPerDp, weather, includeBackground,
            valueText, reserveTrendArrowSpace, drawClock = !nativeClock
        )
        val layout = if (nativeClock) R.layout.home_widget_native_clock else R.layout.home_widget
        return RemoteViews(context.packageName, layout).apply {
            setImageViewBitmap(R.id.home_widget_image, bitmap)
            setContentDescription(R.id.home_widget_image, renderer.describe(snapshot))
            if (nativeClock) {
                val weight = clockIds.indices.minBy { abs(300 + it * 100 - config.primaryFontWeight) }
                val id = clockIds[weight]
                clockIds.forEach { setViewVisibility(it, if (it == id) View.VISIBLE else View.GONE) }
                val hasWeather = config.showWeather && weather != null &&
                    (config.showWeatherIcon || config.showTemperature)
                val clockWidth = (w * (if (hasWeather) .30f else .48f) - 12f).coerceAtLeast(1f)
                val fontScale = context.resources.configuration.fontScale
                val selectedSize = min(w * .145f, h * .46f) * config.glucoseScale / 100f
                val fittedSize = min(selectedSize, min(clockWidth / 3.1f, h * .46f) / fontScale)
                val density = context.resources.displayMetrics.density
                setViewPadding(id, (12f * density).roundToInt(), 0,
                    ((w - clockWidth - 12f) * density).roundToInt().coerceAtLeast(0), 0)
                setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, fittedSize)
                val dark = context.resources.configuration.uiMode and 0x30 == 0x20
                setTextColor(id, if (dark || config.background == WidgetBackground.RANGE)
                    Color.WHITE else 0xFF1D1B20.toInt())
            }
        }
    }
}
