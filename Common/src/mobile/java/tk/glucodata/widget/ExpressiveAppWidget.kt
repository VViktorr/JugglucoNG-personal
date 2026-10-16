package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.width
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.DisplayDataState
import tk.glucodata.GlucosePoint
import tk.glucodata.GlucoseUpdateBroadcaster
import tk.glucodata.MainActivity
import tk.glucodata.NotificationChartDrawer
import tk.glucodata.R
import tk.glucodata.WidgetDisplaySource
import tk.glucodata.widgets.WidgetWeather
import tk.glucodata.widgets.WidgetConfig
import tk.glucodata.widgets.WidgetConfigStore
import tk.glucodata.widgets.WidgetSettingsKind
import tk.glucodata.widgets.WidgetFontFamily
import tk.glucodata.widgets.WidgetWeatherSource
import tk.glucodata.ui.util.GlucoseFormatter

class ExpressiveAppWidget : GlanceAppWidget() {
    private companion object {
        private val weatherRefreshPending = AtomicBoolean(false)
        private val weatherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun weightStyle(weight: Int): Int = when {
            weight <= 300 -> R.style.WidgetTextWeight300
            weight <= 400 -> R.style.WidgetTextWeight400
            weight <= 500 -> R.style.WidgetTextWeight500
            weight <= 600 -> R.style.WidgetTextWeight600
            else -> R.style.WidgetTextWeight700
        }

        val HeaderHeight = 88.dp
        val CardGap = 10.dp
        val DividerHeight = 3.dp
        val DividerInset = 26.dp
        const val ChartReservedHeightDp = 92f
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialTick = GlucoseUpdateBroadcaster.tick.value
        val initialState = withContext(Dispatchers.IO) { loadWidgetState(context) }

        provideContent {
            GlanceTheme {
                val tick by GlucoseUpdateBroadcaster.tick.collectAsState()
                val state by produceState(initialValue = initialState, key1 = tick) {
                    if (tick != initialTick) {
                        value = withContext(Dispatchers.IO) { loadWidgetState(context) }
                    }
                }
                WidgetContent(
                    currentDisplay = state.currentDisplay,
                    history = state.history,
                    dataState = state.dataState,
                    activeSensorSerial = state.activeSensorSerial,
                    viewMode = state.viewMode,
                    hasCalibration = state.hasCalibration,
                    weather = state.weather
                )
            }
        }
    }

    private data class WidgetState(
        val currentDisplay: CurrentDisplaySource.Snapshot?,
        val history: List<GlucosePoint>,
        val dataState: DisplayDataState.Status,
        val activeSensorSerial: String?,
        val viewMode: Int,
        val hasCalibration: Boolean,
        val weather: WidgetWeather?
    )

    private fun widgetTypeface(context: Context, config: WidgetConfig, weight: Int): Typeface {
        return try {
            val base = if (config.fontFamily == WidgetFontFamily.IBM_PLEX) {
                ResourcesCompat.getFont(context, R.font.ibm_plex_sans_var)
                    ?: Typeface.create("sans-serif", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else base
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun textBitmap(
        context: Context,
        text: String,
        sizeSp: Float,
        config: WidgetConfig,
        color: Int,
        weight: Int = config.secondaryFontWeight,
        wrapWidthPx: Int? = null
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * context.resources.displayMetrics.scaledDensity
            this.color = color
            typeface = widgetTypeface(context, config, weight)
            if (Build.VERSION.SDK_INT >= 26 && config.fontFamily == WidgetFontFamily.IBM_PLEX) {
                fontVariationSettings = "'wght' $weight, 'wdth' 100"
            }
        }
        val pad = (3f * context.resources.displayMetrics.density).coerceAtLeast(2f)
        if (wrapWidthPx != null) {
            val width = wrapWidthPx.coerceAtLeast(1)
            val textWidth = (width - 2 * pad).roundToInt().coerceAtLeast(1)
            val textPaint = android.text.TextPaint(paint)
            val wrapped = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_OPPOSITE)
                .setIncludePad(false)
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
                .build()
            // Keep the configured font size; the widget gives all wrapped lines their own height.
            return Bitmap.createBitmap(width, (wrapped.height + 2 * pad).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.translate(pad, pad)
                wrapped.draw(canvas)
            }
        }
        val fm = paint.fontMetrics
        val width = (paint.measureText(text) + pad * 2f).roundToInt().coerceAtLeast(1)
        val height = ((fm.descent - fm.ascent) + pad * 2f).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(text, pad, pad - fm.ascent, paint)
        }
    }

    private fun loadWidgetState(context: Context): WidgetState {
        val widgetConfig = WidgetConfigStore.load(context, WidgetSettingsKind.CHART)
        val chartWindowMs = widgetConfig.graphHours * 3_600_000L
        val currentDisplay = WidgetDisplaySource.resolveWidgetSnapshot()
        val chartHistory = WidgetDisplaySource.resolveChartHistory(currentDisplay, chartWindowMs)
        val activeSensorSerial = currentDisplay?.sensorId ?: WidgetDisplaySource.resolveActiveSensorSerial()
        val resolvedDisplay = WidgetDisplaySource.resolveDisplaySnapshot(currentDisplay, chartHistory, activeSensorSerial)
        val viewMode = resolvedDisplay?.viewMode ?: WidgetDisplaySource.resolveViewMode(activeSensorSerial)
        val dataState = WidgetDisplaySource.resolveDataState(resolvedDisplay, chartHistory, activeSensorSerial)
        val hasCalibration = WidgetDisplaySource.hasCalibration(activeSensorSerial, viewMode)
        val app = context.applicationContext
        val weather = if (widgetConfig.showWeather) WidgetWeatherSource.cached(app) else null
        if (widgetConfig.showWeather && WidgetWeatherSource.needsRefresh(app) &&
            weatherRefreshPending.compareAndSet(false, true)) {
            weatherScope.launch {
                try {
                    val previous = WidgetWeatherSource.cached(app)?.observedAt ?: 0L
                    val refreshed = WidgetWeatherSource.load(app)
                    if (refreshed != null && refreshed.observedAt > previous) {
                        ExpressiveWidgetReceiver.updateAll(app)
                    }
                } catch (t: Throwable) {
                    tk.glucodata.Log.stack("ExpressiveWidget", "weather refresh", t)
                } finally {
                    weatherRefreshPending.set(false)
                }
            }
        }
        return WidgetState(
            currentDisplay = resolvedDisplay,
            history = chartHistory,            dataState = dataState,
            activeSensorSerial = activeSensorSerial,
            viewMode = viewMode,            hasCalibration = hasCalibration,
            weather = weather
        )
    }

    @Composable
    private fun WidgetContent(
        currentDisplay: CurrentDisplaySource.Snapshot?,
        history: List<GlucosePoint>,
        dataState: DisplayDataState.Status,
        activeSensorSerial: String?,
        viewMode: Int,
        hasCalibration: Boolean,
        weather: WidgetWeather?
    ) {
        val context = LocalContext.current
        val size = LocalSize.current
        val config = WidgetConfigStore.load(context, WidgetSettingsKind.CHART)

        val isMmol = GlucoseFormatter.isMmolApp()
        val fontSize = 1f
        val glucoseScale = config.glucoseScale / 100f
        val timeScale = config.timeScale / 100f
        val weatherScale = config.weatherScale / 100f
        val detailScale = config.detailScale / 100f
        val primaryFontWeight = config.primaryFontWeight
        val secondaryFontWeight = config.secondaryFontWeight
        val useSystemFont = config.fontFamily == WidgetFontFamily.SYSTEM
        val showArrow = config.showArrow
        val arrowScale = glucoseScale.coerceIn(0.7f, 1.8f)

        val displayValue = currentDisplay?.primaryValue ?: 0f
        val glucoseColor = remember(displayValue, isMmol, config.rangeColors) {
            if (config.rangeColors) {
                NotificationChartDrawer.getGlucoseColor(context, displayValue, isMmol)
            } else {
                val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
                if (dark) Color.WHITE else 0xFF1D1B20.toInt()
            }
        }
        val valueText = currentDisplay?.fullFormatted
        val rate = currentDisplay?.rate ?: Float.NaN

        val valueBitmap = remember(valueText, glucoseColor, fontSize, glucoseScale, primaryFontWeight, useSystemFont) {
            valueText?.let {
                NotificationChartDrawer.drawGlucoseText(
                    context,
                    it,
                    glucoseColor,
                    fontSize * glucoseScale,
                    primaryFontWeight,
                    useSystemFont
                )
            }
        }

        val arrowBitmap = remember(rate, showArrow, arrowScale, glucoseColor, isMmol) {
            if (showArrow && rate.isFinite()) {
                NotificationChartDrawer.drawArrow(context, rate, isMmol, glucoseColor, arrowScale)
            } else {
                null
            }
        }

        val density = context.resources.displayMetrics.density
        val darkSurface = (context.resources.configuration.uiMode and 0x30) == 0x20
        val primaryTextColor = if (darkSurface) Color.WHITE else 0xFF1D1B20.toInt()
        val secondaryTextColor = Color.argb(
            200,
            Color.red(primaryTextColor),
            Color.green(primaryTextColor),
            Color.blue(primaryTextColor)
        )
        val fallbackText = remember(dataState, activeSensorSerial) {
            tk.glucodata.widgets.WidgetDataSource.statusText(context, activeSensorSerial, dataState)
                ?: context.getString(R.string.novalue)
        }
        val fallbackBitmap = remember(
            valueBitmap, fallbackText, config.fontFamily, secondaryFontWeight,
            detailScale, secondaryTextColor, size.width
        ) {
            if (valueBitmap != null) null else textBitmap(
                context, fallbackText, 13f * detailScale, config, secondaryTextColor,
                secondaryFontWeight,
                wrapWidthPx = ((size.width.value - 28f).coerceAtLeast(1f) * density).roundToInt()
            )
        }
        val fallbackHeightDp = fallbackBitmap?.let { it.height / density } ?: 0f
        val chartReservedHeightDp = ChartReservedHeightDp + fallbackHeightDp
        val showChart = history.size >= 2 && size.height >= 144.dp &&
            (fallbackBitmap == null || size.height.value - chartReservedHeightDp >= 60f)
        val chartBitmap = remember(history, showChart, size, isMmol, viewMode, hasCalibration, activeSensorSerial, config.graphHours, config.showTargetBand, chartReservedHeightDp) {
            if (!showChart) {
                null
            } else {
                val widthPx = (size.width.value.coerceAtLeast(96f) * density).roundToInt()
                val heightPx = ((size.height.value - chartReservedHeightDp).coerceAtLeast(60f) * density).roundToInt()
                NotificationChartDrawer.drawChart(
                    context,
                    history,
                    widthPx,
                    heightPx,
                    isMmol,
                    viewMode,
                    config.showTargetBand,
                    hasCalibration,
                    true,
                    activeSensorSerial,
                    config.graphHours * 3_600_000L
                )
            }
        }

        val bgRgb = when (config.background) {
            tk.glucodata.widgets.WidgetBackground.TRANSPARENT -> null
            tk.glucodata.widgets.WidgetBackground.SURFACE ->
                if (darkSurface) 0xFF1C1B1F.toInt() else 0xFFF7F2FA.toInt()
            tk.glucodata.widgets.WidgetBackground.RANGE ->
                NotificationChartDrawer.getGlucoseColor(context, displayValue, isMmol)
        }
        val backgroundModifier = if (bgRgb == null || config.opacity == 0) GlanceModifier
        else GlanceModifier.background(ComposeColor(Color.argb(
            (config.opacity * 255f / 100f).roundToInt().coerceIn(0, 255),
            Color.red(bgRgb), Color.green(bgRgb), Color.blue(bgRgb)
        )))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .then(backgroundModifier)
                .cornerRadius(config.cornerRadiusDp.coerceIn(0, 24).dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(HeaderHeight)

                    .cornerRadius(config.cornerRadiusDp.coerceIn(0, 24).dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Use Android's native TextClock instead of painting the current time into
                    // a bitmap. The launcher/system then keeps time and date current even when
                    // Juggluco is not running or no glucose update arrives.
                    val systemClockViews = remember(
                        config.showDate,
                        config.textScale,
                        config.glucoseScale,
                        config.detailScale,
                        config.fontFamily,
                        config.primaryFontWeight,
                        config.secondaryFontWeight,
                        primaryTextColor,
                        secondaryTextColor
                    ) {
                        val baseScale = 1f
                        val clockScale = baseScale * glucoseScale
                        val dateScale = baseScale * detailScale
                        val clockLayout = when (config.fontFamily) {
                            WidgetFontFamily.IBM_PLEX -> when (config.primaryFontWeight) {
                                300 -> R.layout.expressive_clock_ibm_300
                                500 -> R.layout.expressive_clock_ibm_500
                                600 -> R.layout.expressive_clock_ibm_600
                                700 -> R.layout.expressive_clock_ibm_700
                                else -> R.layout.expressive_widget_system_clock_ibm
                            }
                            WidgetFontFamily.SYSTEM -> when (config.primaryFontWeight) {
                                300 -> R.layout.expressive_clock_system_300
                                500 -> R.layout.expressive_clock_system_500
                                600 -> R.layout.expressive_clock_system_600
                                700 -> R.layout.expressive_clock_system_700
                                else -> R.layout.expressive_widget_system_clock
                            }
                        }
                        RemoteViews(context.packageName, clockLayout).apply {
                            setTextViewTextSize(
                                R.id.expressive_widget_clock,
                                android.util.TypedValue.COMPLEX_UNIT_SP,
                                27f * clockScale
                            )
                            setTextColor(R.id.expressive_widget_clock, primaryTextColor)
                            setTextViewTextSize(
                                R.id.expressive_widget_date,
                                android.util.TypedValue.COMPLEX_UNIT_SP,
                                11f * dateScale
                            )
                            setTextColor(R.id.expressive_widget_date, secondaryTextColor)
                            setViewVisibility(
                                R.id.expressive_widget_date,
                                if (config.showDate) View.VISIBLE else View.GONE
                            )
                        }
                    }
                    AndroidRemoteViews(
                        remoteViews = systemClockViews,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    val weatherSymbol = remember(weather, config.showWeather, config.showWeatherIcon) {
                        if (!config.showWeather || !config.showWeatherIcon) null else weather?.let { w ->
                            when (w.weatherCode) {
                                0 -> "☀"
                                1, 2 -> "⛅"
                                3 -> "☁"
                                in 45..48 -> "≋"
                                in 51..67, in 80..82 -> "☂"
                                in 71..77, in 85..86 -> "❄"
                                in 95..99 -> "ϟ"
                                else -> "☁"
                            }
                        }
                    }
                    val temperatureText = remember(weather, config.showWeather, config.showTemperature) {
                        if (!config.showWeather || !config.showTemperature) null
                        else weather?.let { "${it.temperatureC}°" }
                    }
                    if (weatherSymbol != null || temperatureText != null) {
                        Row(
                            modifier = GlanceModifier.padding(start = 4.dp, end = (4 + config.weatherShiftDp).dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (weatherSymbol != null) {
                                val iconBitmap = remember(weatherSymbol, config.fontFamily, config.secondaryFontWeight, config.textScale, config.weatherScale, config.weatherIconScale, primaryTextColor) {
                                    textBitmap(context, weatherSymbol, 19f * weatherScale * (config.weatherIconScale / 100f), config, primaryTextColor, secondaryFontWeight)
                                }
                                Image(provider = ImageProvider(iconBitmap), contentDescription = "Weather")
                            }
                            if (weatherSymbol != null && temperatureText != null) Spacer(GlanceModifier.width(3.dp))
                            if (temperatureText != null) {
                                val temperatureBitmap = remember(temperatureText, config.fontFamily, config.secondaryFontWeight, config.textScale, config.weatherScale, config.temperatureScale, primaryTextColor) {
                                    textBitmap(context, temperatureText, 17f * weatherScale * (config.temperatureScale / 100f), config, primaryTextColor, secondaryFontWeight)
                                }
                                Image(provider = ImageProvider(temperatureBitmap), contentDescription = "Temperature")
                            }
                        }
                    }

                    if (valueBitmap != null) {
                        Row(
                            modifier = GlanceModifier.defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalAlignment = Alignment.End
                        ) {
                            Image(
                                provider = ImageProvider(valueBitmap),
                                contentDescription = "Glucose"
                            )
                            if (arrowBitmap != null) {
                                Spacer(GlanceModifier.width(4.dp))
                                Image(
                                    provider = ImageProvider(arrowBitmap),
                                    contentDescription = "Trend"
                                )
                            }
                        }
                    }
                }
            }

            if (fallbackBitmap != null) {
                // A dedicated row cannot be squeezed by the clock/weather columns.
                Image(
                    provider = ImageProvider(fallbackBitmap),
                    contentDescription = fallbackText,
                    modifier = GlanceModifier.fillMaxWidth()
                        .height(fallbackHeightDp.dp)
                        .padding(horizontal = 14.dp),
                    contentScale = androidx.glance.layout.ContentScale.FillBounds
                )
            }

            if (chartBitmap != null) {
                Spacer(GlanceModifier.height(2.dp))

//                Box(
//                    modifier = GlanceModifier
//                        .fillMaxWidth()
//                        .padding(horizontal = DividerInset)
//                        .height(DividerHeight)
//                        .background(GlanceTheme.colors.onSurfaceVariant)
//                        .cornerRadius(99.dp)
//                ) {}
//
//                Spacer(GlanceModifier.height(CardGap))

                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .cornerRadius(config.cornerRadiusDp.coerceIn(0, 24).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = "Chart",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = androidx.glance.layout.ContentScale.FillBounds
                    )
                }
            }
        }
    }
}
