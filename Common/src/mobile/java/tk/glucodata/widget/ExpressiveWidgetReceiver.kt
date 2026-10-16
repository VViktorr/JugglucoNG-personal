package tk.glucodata.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import tk.glucodata.GlucoseUpdateBroadcaster
import tk.glucodata.Log

class ExpressiveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ExpressiveAppWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GlucoseUpdateBroadcaster.ACTION_GLUCOSE_UPDATE) {
            // Do not rely on an in-process Flow observer to keep the launcher copy alive.
            // The host may have discarded the previous RemoteViews/Glance tree while the
            // process still reports an observer. Always push an explicit widget update.
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
        private const val LOG_ID = "ExpressiveWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val updating = AtomicBoolean(false)
        private val updateAgain = AtomicBoolean(false)
        
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
            // Coalesce overlapping sensor broadcasts. Preserve the last update while
            // avoiding multiple concurrent Glance history reads and chart renders.
            if (!updating.compareAndSet(false, true)) {
                updateAgain.set(true)
                return
            }
            try {
                do {
                    updateAgain.set(false)
                    val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
                    val widget = ExpressiveAppWidget()
                    val glanceIds = manager.getGlanceIds(ExpressiveAppWidget::class.java)
                    glanceIds.forEach { glanceId ->
                        try {
                            widget.update(context, glanceId)
                        } catch (th: Throwable) {
                            Log.stack(LOG_ID, "update $glanceId", th)
                        }
                    }
                } while (updateAgain.getAndSet(false))
            } finally {
                updating.set(false)
                if (updateAgain.getAndSet(false)) updateAll(context)
            }
        }
    }
}
