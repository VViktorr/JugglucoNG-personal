package tk.glucodata.widgets

import android.content.Context
import android.widget.RemoteViews
import tk.glucodata.R
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared clock/glucose layout; bundled fonts are rasterized locally for the launcher. */
object ClockWeatherWidgetViews {
    fun create(
        context: Context,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        w: Float,
        h: Float,
        pxPerDp: Float,
        weather: WidgetWeather?,
        includeBackground: Boolean = true,
        valueText: String = snapshot.valueText,
        reserveTrendArrowSpace: Boolean = false
    ): RemoteViews {
        val renderer = WidgetRenderer(context)
        if (WidgetClockRefresh.usesBitmapClock(context)) {
            val bitmap = renderer.renderClockHeader(config, snapshot, w, h, pxPerDp,
                weather, includeBackground, valueText, reserveTrendArrowSpace)
            return RemoteViews(context.packageName, R.layout.home_widget).apply {
                setImageViewBitmap(R.id.home_widget_image, bitmap)
                setContentDescription(R.id.home_widget_image, renderer.describe(snapshot))
            }
        }
        val views = RemoteViews(context.packageName,
            when (config.fontFamily) {
                WidgetFontFamily.HARMONYOS -> R.layout.home_widget_clock_weather_harmonyos
                WidgetFontFamily.COMFORTAA -> R.layout.home_widget_clock_weather_comfortaa
                WidgetFontFamily.UBUNTU -> R.layout.home_widget_clock_weather_ubuntu
                WidgetFontFamily.ROBOTO_CONDENSED -> R.layout.home_widget_clock_weather_robotocondensed
                WidgetFontFamily.OSWALD -> R.layout.home_widget_clock_weather_oswald
                WidgetFontFamily.RUBIK -> R.layout.home_widget_clock_weather_rubik
                WidgetFontFamily.POPPINS -> R.layout.home_widget_clock_weather_poppins
                WidgetFontFamily.PLUS_JAKARTA -> R.layout.home_widget_clock_weather_plusjakartasans
                WidgetFontFamily.NOTO_SANS -> R.layout.home_widget_clock_weather_notosans
                WidgetFontFamily.INTER -> R.layout.home_widget_clock_weather_inter
                WidgetFontFamily.DM_SANS -> R.layout.home_widget_clock_weather_dmsans
                WidgetFontFamily.ROBOTO_FLEX -> R.layout.home_widget_clock_weather_robotoflex
                else -> when {
                config.mainFontFamily == WidgetFontFamily.IBM_PLEX && config.detailFontFamily == WidgetFontFamily.IBM_PLEX ->
                    R.layout.home_widget_clock_weather_ibm
                config.mainFontFamily == WidgetFontFamily.SYSTEM && config.detailFontFamily == WidgetFontFamily.IBM_PLEX ->
                    R.layout.home_widget_clock_weather_system_main_ibm_detail
                config.mainFontFamily == WidgetFontFamily.IBM_PLEX && config.detailFontFamily == WidgetFontFamily.SYSTEM ->
                    R.layout.home_widget_clock_weather_ibm_main_system_detail
                else -> R.layout.home_widget_clock_weather
                }
            }
        ).apply {
            val clockSize=min(w*0.145f,h*0.46f)*config.glucoseScale/100f
            val dateSize=min(w*0.042f,h*0.19f)*config.detailScale/100f
            val textColor=if((context.resources.configuration.uiMode and 0x30)==0x20 || config.background==WidgetBackground.RANGE) android.graphics.Color.WHITE else 0xFF1D1B20.toInt()
            // Predefined TextClock variants retain system time updates.
            // RemoteViews visibility, size and color are supported operations.
            val weights = intArrayOf(300, 400, 500, 600, 700)
            // Older preferences can contain intermediate variable-font weights.
            val primaryWeight = weights.minByOrNull { kotlin.math.abs(it - config.primaryFontWeight) } ?: 400
            val secondaryWeight = weights.minByOrNull { kotlin.math.abs(it - config.secondaryFontWeight) } ?: 400
            val clockIds = intArrayOf(R.id.home_widget_clock_300, R.id.home_widget_clock_400, R.id.home_widget_clock_500, R.id.home_widget_clock_600, R.id.home_widget_clock_700)
            val dateIds = intArrayOf(R.id.home_widget_date_300, R.id.home_widget_date_400, R.id.home_widget_date_500, R.id.home_widget_date_600, R.id.home_widget_date_700)
            val glucoseIds = intArrayOf(R.id.home_widget_glucose_300, R.id.home_widget_glucose_400, R.id.home_widget_glucose_500, R.id.home_widget_glucose_600, R.id.home_widget_glucose_700)
            val detailIds = intArrayOf(R.id.home_widget_detail_300, R.id.home_widget_detail_400, R.id.home_widget_detail_500, R.id.home_widget_detail_600, R.id.home_widget_detail_700)
            val staleIds = intArrayOf(R.id.home_widget_stale_300, R.id.home_widget_stale_400, R.id.home_widget_stale_500, R.id.home_widget_stale_600, R.id.home_widget_stale_700)
            val staleStatus = !snapshot.hasFreshReading
            // The chart header can have different proportions and larger arrows than
            // the standalone clock widget. Reserve the full arrow box plus an 8dp gap.
            val glucoseEndPaddingDp = if (config.showArrow &&
                snapshot.hasFreshReading && snapshot.rate.isFinite()) {
                val arrowSizeDp = min(min(w * 0.09f, h * 0.32f) * config.glucoseScale / 100f, min(w * .10f, h * .40f))
                maxOf(12f, w * 0.07f + arrowSizeDp / 2f + 8f)
            } else 12f
            // Status occupies the detail row, never the weather/value row.
            val density = context.resources.displayMetrics.density
            setViewPadding(R.id.home_widget_stale_container,
                (w * .51f * density).roundToInt(), (h * .5f * density).roundToInt(), 0, 0)
            setViewVisibility(R.id.home_widget_stale_container, if (staleStatus) android.view.View.VISIBLE else android.view.View.GONE)
            val detailText = if (!snapshot.hasFreshReading) snapshot.statusText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.novalue) else buildList {
                if (config.showDelta) snapshot.delta?.let { add(renderer.deltaText(it, snapshot.isMmol)) }
                if (config.showUnit) add(snapshot.unitText)
                if (config.showTime) snapshot.current?.let { add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis))) }
            }.joinToString("  ")
            val glucoseText = if (staleStatus) "—" else valueText.takeIf { it.isNotBlank() } ?: snapshot.valueText.takeIf { it.isNotBlank() } ?: "—"
            val glucoseColor = if (snapshot.hasFreshReading) renderer.glucoseTextColor(snapshot, config) else textColor
            val statusPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tk.glucodata.FontCatalog.typeface(context, config.fontFamily, config.secondaryFontWeight, false)
                textSize = dateSize * context.resources.displayMetrics.scaledDensity
            }
            val statusMetrics = statusPaint.fontMetrics
            val statusFit = minOf(1f,
                ((w * .49f - 12f) * density - 2f).coerceAtLeast(1f) / statusPaint.measureText(detailText).coerceAtLeast(1f),
                (h * .5f * density - 2f).coerceAtLeast(1f) / (statusMetrics.bottom - statusMetrics.top).coerceAtLeast(1f))
            weights.indices.forEach { index ->
                val clockId = clockIds[index]
                val dateId = dateIds[index]
                val detailId = detailIds[index]
                val glucoseId = glucoseIds[index]
                val staleId = staleIds[index]
                setViewVisibility(clockId, if (weights[index] == primaryWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(glucoseId, if (weights[index] == primaryWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(glucoseId, glucoseText)
                setTextViewTextSize(glucoseId, android.util.TypedValue.COMPLEX_UNIT_SP, clockSize)
                setTextColor(glucoseId, glucoseColor)
                setViewPadding(glucoseId, 0, 0,
                    (glucoseEndPaddingDp * context.resources.displayMetrics.density).roundToInt(), 0)
                setViewVisibility(dateId, if (config.showDate && weights[index] == secondaryWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewTextSize(clockId, android.util.TypedValue.COMPLEX_UNIT_SP, clockSize)
                setTextColor(clockId, textColor)
                setViewVisibility(detailId, if (!staleStatus && detailText.isNotBlank() && weights[index] == secondaryWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(detailId, detailText)
                setInt(detailId, "setMaxLines", 1)
                // Reuse the space above the status instead of shrinking its font.
                val halfWidth = (w * 0.48f * density).roundToInt()
                setViewPadding(detailId, 0, 0, (12f * density).roundToInt(), 0)
                setViewPadding(dateId, (14f * density).roundToInt(), 0,
                    halfWidth, 0)
                setViewVisibility(staleId, if (staleStatus && weights[index] == secondaryWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(staleId, detailText)
                setInt(staleId, "setMaxLines", 1)
                setViewPadding(staleId, 0, 0, (12f * density).roundToInt(), 0)
                setTextViewTextSize(staleId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize * statusFit)
                setTextColor(staleId, textColor)
                setTextViewTextSize(detailId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)
                setTextColor(detailId, textColor)
                setTextViewTextSize(dateId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)
                setTextColor(dateId, textColor)
            }
            setImageViewBitmap(R.id.home_widget_image,
                renderer.render(WidgetKind.CLOCK_WEATHER, config, snapshot, w, h, pxPerDp,
                    weather, includeBackground = includeBackground))
            setContentDescription(R.id.home_widget_image, renderer.describe(snapshot))
        }
        return views
    }
}
