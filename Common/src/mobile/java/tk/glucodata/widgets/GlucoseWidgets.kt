package tk.glucodata.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import tk.glucodata.GlucoseUpdateBroadcaster

abstract class BaseGlucoseWidget(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        WidgetUpdater.update(context, kind, ids) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        val pending = goAsync()
        WidgetUpdater.update(context, kind, intArrayOf(id)) { pending.finish() }
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.requestUpdateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GlucoseUpdateBroadcaster.ACTION_GLUCOSE_UPDATE) {
            // Every provider receives the same glucose broadcast. Coalesce them
            // into one shared snapshot/history load and one render pass.
            val pending = goAsync()
            WidgetUpdater.requestUpdateAll(context) { pending.finish() }
            return
        }
        super.onReceive(context, intent)
    }
}

class MinimalGlucoseWidget : BaseGlucoseWidget(WidgetKind.MINIMAL)
class CompactGlucoseWidget : BaseGlucoseWidget(WidgetKind.COMPACT)
class TrendGlucoseWidget : BaseGlucoseWidget(WidgetKind.TREND)
class DialGlucoseWidget : BaseGlucoseWidget(WidgetKind.DIAL)
class RangeGlucoseWidget : BaseGlucoseWidget(WidgetKind.RANGE)
class ClockWeatherWidget : BaseGlucoseWidget(WidgetKind.CLOCK_WEATHER)
