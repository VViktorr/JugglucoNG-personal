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
            val clockIds = intArrayOf(R.id.home_widget_clock_300, R.id.home_widget_clock_400, R.id.home_widget_clock_500, R.id.home_widget_clock_600, R.id.home_widget_clock_700)
            val dateIds = intArrayOf(R.id.home_widget_date_300, R.id.home_widget_date_400, R.id.home_widget_date_500, R.id.home_widget_date_600, R.id.home_widget_date_700)
            val glucoseIds = intArrayOf(R.id.home_widget_glucose_300, R.id.home_widget_glucose_400, R.id.home_widget_glucose_500, R.id.home_widget_glucose_600, R.id.home_widget_glucose_700)
            val detailIds = intArrayOf(R.id.home_widget_detail_300, R.id.home_widget_detail_400, R.id.home_widget_detail_500, R.id.home_widget_detail_600, R.id.home_widget_detail_700)
            val staleIds = intArrayOf(R.id.home_widget_stale_300, R.id.home_widget_stale_400, R.id.home_widget_stale_500, R.id.home_widget_stale_600, R.id.home_widget_stale_700)
            val staleStatus = !snapshot.hasFreshReading
            // The chart header can have different proportions and larger arrows than
            // the standalone clock widget. Reserve the full arrow box plus an 8dp gap.
            val glucoseEndPaddingDp = if (reserveTrendArrowSpace && config.showArrow &&
                snapshot.hasFreshReading && snapshot.rate.isFinite()) {
                val arrowSizeDp = min(w * 0.09f, h * 0.32f) * config.glucoseScale / 100f
                maxOf(38f, w * 0.07f + arrowSizeDp / 2f + 8f)
            } else 38f
            // Keep stale text in its own measured column, outside the weather bitmap.
            // Weather can reach 67.1% of the width at its rightmost configured position.
            val hasWeather = config.showWeather && weather != null &&
                (config.showWeatherIcon || config.showTemperature)
            val staleStartDp = if (hasWeather) w * 0.68f + 8f else w * 0.48f
            setViewPadding(R.id.home_widget_stale_container,
                (staleStartDp * pxPerDp).roundToInt(), 0, 0, 0)
            setViewVisibility(R.id.home_widget_stale_container, if (staleStatus) android.view.View.VISIBLE else android.view.View.GONE)
            val detailText = if (!snapshot.hasFreshReading) snapshot.statusText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.novalue) else buildList {
                if (config.showDelta) snapshot.delta?.let { add(renderer.deltaText(it, snapshot.isMmol)) }
                if (config.showUnit) add(snapshot.unitText)
                if (config.showTime) snapshot.current?.let { add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis))) }
            }.joinToString("  ")
            val glucoseText = valueText
            val glucoseColor = if (snapshot.hasFreshReading) renderer.glucoseTextColor(snapshot, config) else textColor
            weights.indices.forEach { index ->
                val clockId = clockIds[index]
                val dateId = dateIds[index]
                val detailId = detailIds[index]
                val glucoseId = glucoseIds[index]
                val staleId = staleIds[index]
                setViewVisibility(clockId, if (weights[index] == config.primaryFontWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setViewVisibility(glucoseId, if (!staleStatus && weights[index] == config.primaryFontWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(glucoseId, glucoseText)
                setTextViewTextSize(glucoseId, android.util.TypedValue.COMPLEX_UNIT_SP, clockSize)
                setTextColor(glucoseId, glucoseColor)
                if (reserveTrendArrowSpace) {
                    setViewPadding(glucoseId, 0, 0,
                        (glucoseEndPaddingDp * pxPerDp).roundToInt(), 0)
                }
                setViewVisibility(dateId, if (config.showDate && weights[index] == config.secondaryFontWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewTextSize(clockId, android.util.TypedValue.COMPLEX_UNIT_SP, clockSize)
                setTextColor(clockId, textColor)
                setViewVisibility(detailId, if (!staleStatus && detailText.isNotBlank() && weights[index] == config.secondaryFontWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(detailId, detailText)
                setInt(detailId, "setMaxLines", 1)
                // Reuse the space above the status instead of shrinking its font.
                val density = context.resources.displayMetrics.density
                val halfWidth = (w * 0.48f * density).roundToInt()
                setViewPadding(detailId, 0, 0, (12f * density).roundToInt(), 0)
                setViewPadding(dateId, (14f * density).roundToInt(), 0,
                    if (staleStatus) halfWidth else 0, 0)
                setViewVisibility(staleId, if (staleStatus && weights[index] == config.secondaryFontWeight) android.view.View.VISIBLE else android.view.View.GONE)
                setTextViewText(staleId, detailText)
                setViewPadding(staleId, 0, 0, (12f * density).roundToInt(), 0)
                setTextViewTextSize(staleId, android.util.TypedValue.COMPLEX_UNIT_SP, dateSize)
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
        // A launcher inflates RemoteViews with a restricted context, so custom
        // font resources on its TextViews/TextClocks are intentionally ignored.
        // Render the same layout in our process and send only its bitmap instead.
        return if (WidgetClockRefresh.usesBitmapClock(context)) {
            bitmapViews(context, views, config, w, h, pxPerDp, renderer.describe(snapshot))
        } else views
    }

    private fun bitmapViews(
        context: Context,
        nativeViews: RemoteViews,
        config: WidgetConfig,
        w: Float,
        h: Float,
        pxPerDp: Float,
        description: String
    ): RemoteViews {
        val draw = java.util.concurrent.Callable {
            val local = nativeViews.apply(context, null)
            val now = System.currentTimeMillis()
            val primaryIds = setOf(
                R.id.home_widget_clock_300, R.id.home_widget_clock_400, R.id.home_widget_clock_500,
                R.id.home_widget_clock_600, R.id.home_widget_clock_700,
                R.id.home_widget_glucose_300, R.id.home_widget_glucose_400, R.id.home_widget_glucose_500,
                R.id.home_widget_glucose_600, R.id.home_widget_glucose_700
            )
            fun applyFont(view: android.view.View) {
                if (view is android.widget.TextView) {
                    val weight = if (view.id in primaryIds) config.primaryFontWeight else config.secondaryFontWeight
                    // These are local Views, so setTypeface is supported. Never send
                    // it as a RemoteViews action or use the application's font here.
                    view.setFontVariationSettings(null)
                    view.typeface = tk.glucodata.FontCatalog.typeface(context, config.fontFamily, weight, false)
                    if (view is android.widget.TextClock) {
                        val pattern = if (android.text.format.DateFormat.is24HourFormat(context))
                            view.format24Hour else view.format12Hour
                        view.text = android.text.format.DateFormat.format(pattern ?: "HH:mm", now)
                    }
                }
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) applyFont(view.getChildAt(index))
                }
            }
            applyFont(local)
            // Measure in the same dp/sp coordinates as the original launcher layout,
            // then draw into the caller's existing Binder-safe pixel budget.
            val density = context.resources.displayMetrics.density
            val width = (w * density).roundToInt().coerceAtLeast(1)
            val height = (h * density).roundToInt().coerceAtLeast(1)
            local.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY)
            )
            local.layout(0, 0, width, height)
            val bitmap = android.graphics.Bitmap.createBitmap(
                (w * pxPerDp).roundToInt().coerceAtLeast(1),
                (h * pxPerDp).roundToInt().coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(bitmap.width.toFloat() / width, bitmap.height.toFloat() / height)
            local.draw(canvas)
            val accessibility = buildList {
                add(description)
                fun addClocks(view: android.view.View) {
                    if (view.visibility != android.view.View.VISIBLE) return
                    if (view is android.widget.TextClock) add(view.text.toString())
                    if (view is android.view.ViewGroup) {
                        for (index in 0 until view.childCount) addClocks(view.getChildAt(index))
                    }
                }
                addClocks(local)
            }.joinToString(", ")
            RemoteViews(context.packageName, R.layout.home_widget).apply {
                setImageViewBitmap(R.id.home_widget_image, bitmap)
                setContentDescription(R.id.home_widget_image, accessibility)
            }
        }
        return try {
            // TextClock owns a Handler. Inflate, configure and draw it on the
            // main looper, even when the widget updater runs on its worker thread.
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) draw.call()
            else {
                val task = java.util.concurrent.FutureTask(draw)
                android.os.Handler(android.os.Looper.getMainLooper()).post(task)
                try { task.get(5, java.util.concurrent.TimeUnit.SECONDS) }
                catch (failure: Exception) { task.cancel(false); throw failure }
            }
        } catch (failure: Exception) {
            tk.glucodata.Log.stack("WidgetFonts", "Local clock rendering failed; native fallback", failure)
            nativeViews
        }
    }
}
