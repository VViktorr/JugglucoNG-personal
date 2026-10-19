package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import tk.glucodata.GlucoseUpdateBroadcaster
import tk.glucodata.MainActivity
import tk.glucodata.NotificationChartDrawer
import tk.glucodata.widgets.ClockWeatherWidgetViews
import tk.glucodata.widgets.WidgetBackground
import tk.glucodata.widgets.WidgetConfig
import tk.glucodata.widgets.WidgetConfigStore
import tk.glucodata.widgets.WidgetDataSource
import tk.glucodata.widgets.WidgetSettingsKind
import tk.glucodata.widgets.WidgetSnapshot
import tk.glucodata.widgets.WidgetWeather
import tk.glucodata.widgets.WidgetWeatherSource

class ExpressiveAppWidget : GlanceAppWidget() {
    private companion object {
        private val weatherRefreshPending = AtomicBoolean(false)
        private val weatherScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
                WidgetContent(state)
            }
        }
    }

    private data class WidgetState(
        val config: WidgetConfig,
        val snapshot: WidgetSnapshot,
        val weather: WidgetWeather?
    )

    private fun loadWidgetState(context: Context): WidgetState {
        val config = WidgetConfigStore.load(context, WidgetSettingsKind.CHART)
        // Same reading freshness, delta, units and status as ClockWeatherWidget.
        // Keep the chart's own history window and saved appearance.
        val snapshot = WidgetDataSource.load(context, config.graphHours * 3_600_000L)
        val app = context.applicationContext
        val weather = if (config.showWeather) WidgetWeatherSource.cached(app) else null
        if (config.showWeather && WidgetWeatherSource.needsRefresh(app) &&
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
        return WidgetState(config, snapshot, weather)
    }

    @Composable
    private fun WidgetContent(state: WidgetState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val config = state.config
        val snapshot = state.snapshot
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.displayMetrics.scaledDensity / density
        val headerHeightDp = ChartWidgetSizing.headerHeightDp(
            size.height.value, config, fontScale
        )
        val chartHeightDp = ChartWidgetSizing.chartHeightDp(size.height.value, headerHeightDp)
        val showChart = snapshot.history.size >= 2 && chartHeightDp >= 60f
        val headerViews = remember(snapshot, state.weather, config, size.width, headerHeightDp) {
            ClockWeatherWidgetViews.create(
                context, config, snapshot, size.width.value, headerHeightDp, density,
                state.weather, includeBackground = false,
                valueText = snapshot.current?.fullFormatted ?: snapshot.valueText,
                reserveTrendArrowSpace = true
            )
        }
        val chartBitmap = remember(
            snapshot, showChart, size.width, chartHeightDp, config.graphHours, config.showTargetBand
        ) {
            if (!showChart) null else NotificationChartDrawer.drawChart(
                context,
                snapshot.history,
                (size.width.value.coerceAtLeast(1f) * density).roundToInt().coerceAtLeast(1),
                (chartHeightDp * density).roundToInt().coerceAtLeast(1),
                snapshot.isMmol,
                snapshot.viewMode,
                config.showTargetBand,
                snapshot.hasCalibration,
                true,
                snapshot.sensorSerial,
                config.graphHours * 3_600_000L
            )
        }

        val darkSurface = (context.resources.configuration.uiMode and 0x30) == 0x20
        val bgRgb = when (config.background) {
            WidgetBackground.TRANSPARENT -> null
            WidgetBackground.SURFACE ->
                if (darkSurface) 0xFF1C1B1F.toInt() else 0xFFF7F2FA.toInt()
            WidgetBackground.RANGE -> NotificationChartDrawer.getGlucoseColor(
                context, snapshot.current?.primaryValue ?: 0f, snapshot.isMmol
            )
        }
        val backgroundModifier = if (bgRgb == null || config.opacity == 0) GlanceModifier
        else GlanceModifier.background(ComposeColor(Color.argb(
            (config.opacity * 255f / 100f).roundToInt().coerceIn(0, 255),
            Color.red(bgRgb), Color.green(bgRgb), Color.blue(bgRgb)
        )))

        Column(
            modifier = GlanceModifier.fillMaxSize()
                .then(backgroundModifier)
                .cornerRadius(config.cornerRadiusDp.coerceIn(0, 24).dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Native TextClock keeps ticking without a sensor update or running activity.
            AndroidRemoteViews(
                remoteViews = headerViews,
                modifier = GlanceModifier.fillMaxWidth().height(headerHeightDp.dp)
            )
            if (chartBitmap != null) {
                Spacer(GlanceModifier.height(ChartWidgetSizing.GAP_DP.dp))
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                        .cornerRadius(config.cornerRadiusDp.coerceIn(0, 24).dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(chartBitmap),
                        contentDescription = context.getString(tk.glucodata.R.string.notification_chart_accessibility),
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = androidx.glance.layout.ContentScale.FillBounds
                    )
                }
            }
        }
    }
}
