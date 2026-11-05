package tk.glucodata.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.glucodata.FontCatalog
import tk.glucodata.widget.ExpressiveWidgetReceiver

/** Refresh bitmap clock text even when no new sensor reading arrives. */
object WidgetClockRefresh {
    private var installed = false
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    // TextClock now ticks in the launcher for every font choice.
    fun usesBitmapClock(context: Context): Boolean = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!usesBitmapClock(app)) return
                mutableRevision.value += 1L
                val manager = AppWidgetManager.getInstance(app)
                if (manager.getAppWidgetIds(ComponentName(app, ClockWeatherWidget::class.java)).isNotEmpty()) {
                    WidgetUpdater.updateAllOfKind(app, WidgetKind.CLOCK_WEATHER)
                }
                if (manager.getAppWidgetIds(ComponentName(app, ExpressiveWidgetReceiver::class.java)).isNotEmpty()) {
                    ExpressiveWidgetReceiver.updateAll(app)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        installed = true
    }
}
