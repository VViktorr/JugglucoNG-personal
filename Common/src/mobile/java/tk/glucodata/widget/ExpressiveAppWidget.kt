package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
        val HeaderHeight = 88.dp
        val CardGap = 10.dp
        val DividerHeight = 3.dp
        val DividerInset = 26.dp
        const val ChartReservedHeightDp = 92f
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialTick = GlucoseUpdateBroadcaster.tick.value
        val initialState = withContext(Dispatchers.IO) { loadWidgetState() }

        provideContent {
            GlanceTheme {
                val tick by GlucoseUpdateBroadcaster.tick.collectAsState()
                val state by produceState(initialValue = initialState, key1 = tick) {
                    if (tick != initialTick) {
                        value = withContext(Dispatchers.IO) { loadWidgetState() }
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

    private fun widgetTypeface(context: Context, config: WidgetConfig): Typeface {
        return try {
            val base = if (config.fontFamily == WidgetFontFamily.IBM_PLEX) {
                ResourcesCompat.getFont(context, R.font.ibm_plex_sans_var)
                    ?: Typeface.create("sans-serif", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, config.fontWeight, false) else base
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    private fun textBitmap(
        context: Context,
        text: String,
        sizeSp: Float,
        config: WidgetConfig,
        color: Int
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * context.resources.displayMetrics.scaledDensity * (config.textScale / 100f)
            this.color = color
            typeface = widgetTypeface(context, config)
            if (Build.VERSION.SDK_INT >= 26 && config.fontFamily == WidgetFontFamily.IBM_PLEX) {
                fontVariationSettings = "'wght' ${config.fontWeight}, 'wdth' 100"
            }
        }
        val fm = paint.fontMetrics
        val pad = (3f * context.resources.displayMetrics.density).coerceAtLeast(2f)
        val width = (paint.measureText(text) + pad * 2f).roundToInt().coerceAtLeast(1)
        val height = ((fm.descent - fm.ascent) + pad * 2f).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(text, pad, pad - fm.ascent, paint)
        }
    }

    private fun loadWidgetState(): WidgetState {
        val widgetConfig = WidgetConfigStore.load(tk.glucodata.Applic.app, WidgetSettingsKind.CHART)
        val chartWindowMs = widgetConfig.graphHours * 3_600_000L
        val currentDisplay = WidgetDisplaySource.resolveWidgetSnapshot()
        val chartHistory = WidgetDisplaySource.resolveChartHistory(currentDisplay, chartWindowMs)
        val activeSensorSerial = currentDisplay?.sensorId ?: WidgetDisplaySource.resolveActiveSensorSerial()
        val resolvedDisplay = WidgetDisplaySource.resolveDisplaySnapshot(currentDisplay, chartHistory, activeSensorSerial)
        val viewMode = resolvedDisplay?.viewMode ?: WidgetDisplaySource.resolveViewMode(activeSensorSerial)
        val dataState = WidgetDisplaySource.resolveDataState(resolvedDisplay, chartHistory, activeSensorSerial)
        val hasCalibration = WidgetDisplaySource.hasCalibration(activeSensorSerial, viewMode)
        val weather = if (!widgetConfig.showWeather) null else runCatching {
            if (WidgetWeatherSource.needsRefresh(tk.glucodata.Applic.app)) {
                WidgetWeatherSource.load(tk.glucodata.Applic.app)
            } else {
                WidgetWeatherSource.cached(tk.glucodata.Applic.app)
            }
        }.getOrElse { WidgetWeatherSource.cached(tk.glucodata.Applic.app) }
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
        val fontSize = config.textScale / 100f
        val fontWeight = config.fontWeight
        val useSystemFont = config.fontFamily == WidgetFontFamily.SYSTEM
        val showArrow = config.showArrow
        val arrowScale = (config.textScale / 100f).coerceIn(0.7f, 1.4f)

        val freshDisplay = currentDisplay?.takeIf { dataState.isFresh }
        val displayValue = freshDisplay?.primaryValue ?: 0f
        val glucoseColor = remember(displayValue, isMmol) {
            NotificationChartDrawer.getGlucoseColor(context, displayValue, isMmol)
        }
        val valueText = freshDisplay?.fullFormatted
        val rate = freshDisplay?.rate ?: Float.NaN

        val valueBitmap = remember(valueText, glucoseColor, fontSize, fontWeight, useSystemFont) {
            valueText?.let {
                NotificationChartDrawer.drawGlucoseText(
                    context,
                    it,
                    glucoseColor,
                    fontSize,
                    fontWeight,
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

        val showChart = history.size >= 2 && size.height >= 144.dp
        val density = context.resources.displayMetrics.density
        val chartBitmap = remember(history, showChart, size, isMmol, viewMode, hasCalibration, activeSensorSerial, config.graphHours, config.showTargetBand) {
            if (!showChart) {
                null
            } else {
                val widthPx = (size.width.value.coerceAtLeast(96f) * density).roundToInt()
                val heightPx = ((size.height.value - ChartReservedHeightDp).coerceAtLeast(60f) * density).roundToInt()
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

        val fallbackText = remember(dataState, activeSensorSerial) {
            tk.glucodata.widgets.WidgetDataSource.statusText(context, activeSensorSerial, dataState)
                ?: context.getString(R.string.novalue)
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(HeaderHeight)
                    .background(GlanceTheme.colors.surface)
                    .cornerRadius(24.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val now = remember(currentDisplay?.timeMillis, dataState) { java.util.Date() }
                    val timeText = remember(now) {
                        android.text.format.DateFormat.getTimeFormat(context).format(now)
                    }
                    val dateText = remember(now, config.showDate) {
                        if (config.showDate) {
                            java.text.SimpleDateFormat("EEE, d MMM", java.util.Locale.getDefault()).format(now)
                        } else ""
                    }

                    val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
                    val primaryTextColor = if (dark) Color.WHITE else 0xFF1D1B20.toInt()
                    val secondaryTextColor = Color.argb(
                        200,
                        Color.red(primaryTextColor),
                        Color.green(primaryTextColor),
                        Color.blue(primaryTextColor)
                    )
                    val timeBitmap = remember(
                        timeText,
                        config.fontFamily,
                        config.fontWeight,
                        config.textScale,
                        primaryTextColor
                    ) {
                        textBitmap(context, timeText, 27f, config, primaryTextColor)
                    }
                    val dateBitmap = remember(
                        dateText,
                        config.fontFamily,
                        config.fontWeight,
                        config.textScale,
                        secondaryTextColor
                    ) {
                        if (dateText.isBlank()) null else textBitmap(context, dateText, 11f, config, secondaryTextColor)
                    }

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Image(provider = ImageProvider(timeBitmap), contentDescription = "Time")
                        if (dateBitmap != null) {
                            Image(provider = ImageProvider(dateBitmap), contentDescription = "Date")
                        }
                    }

                    val weatherText = remember(weather, config.showWeather, config.showWeatherIcon, config.showTemperature) {
                        if (!config.showWeather) null else weather?.let { w ->
                            val symbol = when (w.weatherCode) {
                                0 -> "☀"
                                1, 2 -> "⛅"
                                3 -> "☁"
                                in 45..48 -> "≋"
                                in 51..67, in 80..82 -> "☂"
                                in 71..77, in 85..86 -> "❄"
                                in 95..99 -> "ϟ"
                                else -> "☁"
                            }
                            (if (config.showWeatherIcon) "$symbol " else "") +
                                (if (config.showTemperature) "${w.temperatureC}°" else "")
                        }?.takeIf { it.isNotBlank() }
                    }
                    if (!weatherText.isNullOrBlank()) {
                        val weatherBitmap = remember(
                            weatherText,
                            config.fontFamily,
                            config.fontWeight,
                            config.textScale,
                            primaryTextColor
                        ) {
                            textBitmap(context, weatherText, 17f, config, primaryTextColor)
                        }
                        Image(
                            provider = ImageProvider(weatherBitmap),
                            contentDescription = "Weather",
                            modifier = GlanceModifier.padding(horizontal = 8.dp)
                        )
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
                    } else {
                        val fallbackBitmap = remember(
                            fallbackText,
                            config.fontFamily,
                            config.fontWeight,
                            config.textScale,
                            secondaryTextColor
                        ) {
                            textBitmap(context, fallbackText, 13f, config, secondaryTextColor)
                        }
                        Image(
                            provider = ImageProvider(fallbackBitmap),
                            contentDescription = fallbackText,
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                }
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
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(18.dp),
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