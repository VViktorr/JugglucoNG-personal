package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.glucodata.GlucoseUpdateBroadcaster
import tk.glucodata.Log
import tk.glucodata.MainActivity
import tk.glucodata.TrendArrowAngle
import tk.glucodata.WidgetDisplaySource
import java.util.Date

internal object CompactGlucoseWidgetText {
    fun trendArrow(rate: Float): String {
        if (!rate.isFinite()) return ""
        val rotation = -TrendArrowAngle.rotationDegrees(rate)
        return when {
            rate > 2f -> "↑↑"
            rotation >= 67.5f -> "↑"
            rotation >= 22.5f -> "↗"
            rate < -2f -> "↓↓"
            rotation <= -67.5f -> "↓"
            rotation <= -22.5f -> "↘"
            else -> "→"
        }
    }
}

class CompactGlucoseAppWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    private data class WidgetState(
        val valueText: String,
        val arrowText: String,
        val timeMillis: Long,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialTick = GlucoseUpdateBroadcaster.tick.value
        val initialState = withContext(Dispatchers.IO) { loadState() }

        provideContent {
            val tick by GlucoseUpdateBroadcaster.tick.collectAsState()
            val state by produceState(initialValue = initialState, key1 = tick) {
                if (tick != initialTick) {
                    value = withContext(Dispatchers.IO) { loadState() }
                }
            }
            CompactContent(state)
        }
    }

    private fun loadState(): WidgetState {
        val current = WidgetDisplaySource.resolveWidgetSnapshot()
        val resolved = current ?: run {
            val sensorSerial = WidgetDisplaySource.resolveActiveSensorSerial()
            val history = WidgetDisplaySource.resolveChartHistory()
            WidgetDisplaySource.resolveDisplaySnapshot(null, history, sensorSerial)
        }
        return WidgetState(
            valueText = resolved?.primaryStr?.takeIf { it.isNotBlank() } ?: "—",
            arrowText = CompactGlucoseWidgetText.trendArrow(resolved?.rate ?: Float.NaN),
            timeMillis = resolved?.timeMillis ?: 0L,
        )
    }

    @Composable
    private fun CompactContent(state: WidgetState) {
        val context = LocalContext.current
        val size = LocalSize.current
        val valueSize = if (size.width < 160.dp) 28.sp else 34.sp
        val timeSize = if (size.height < 64.dp) 12.sp else 14.sp
        val headline = if (state.arrowText.isEmpty()) {
            state.valueText
        } else {
            "${state.valueText} ${state.arrowText}"
        }
        val timeText = if (state.timeMillis > 0L) {
            DateFormat.getTimeFormat(context).format(Date(state.timeMillis))
        } else {
            "--:--"
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xB3000000.toInt())))
                .cornerRadius(18.dp)
                .padding(10.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headline,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = valueSize,
                ),
                maxLines = 1,
            )
            Text(
                text = timeText,
                style = TextStyle(
                    color = ColorProvider(Color(0xFFD0D0D0.toInt())),
                    fontSize = timeSize,
                ),
                maxLines = 1,
            )
        }
    }
}

class CompactGlucoseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactGlucoseAppWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GlucoseUpdateBroadcaster.ACTION_GLUCOSE_UPDATE) {
            val pendingResult = goAsync()
            scope.launch {
                try {
                    performUpdate(context.applicationContext)
                } catch (th: Throwable) {
                    Log.stack(LOG_ID, "onReceive update", th)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val LOG_ID = "CompactGlucoseWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @JvmStatic
        fun updateAll(context: Context) {
            scope.launch {
                try {
                    performUpdate(context.applicationContext)
                } catch (th: Throwable) {
                    Log.stack(LOG_ID, "updateAll", th)
                }
            }
        }

        private suspend fun performUpdate(context: Context) {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val widget = CompactGlucoseAppWidget()
            manager.getGlanceIds(CompactGlucoseAppWidget::class.java).forEach { glanceId ->
                try {
                    widget.update(context, glanceId)
                } catch (th: Throwable) {
                    Log.stack(LOG_ID, "update $glanceId", th)
                }
            }
        }
    }
}
