package tk.glucodata.widgets

import android.content.Context
import tk.glucodata.Applic
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.DisplayDataState
import tk.glucodata.GlucosePoint
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.WidgetDisplaySource
import java.util.Calendar
import kotlin.math.abs

data class WidgetRange(
    val veryLow: Float,
    val low: Float,
    val high: Float,
    val veryHigh: Float
)

data class WidgetRangeStats(
    val veryLow: Float,
    val low: Float,
    val inRange: Float,
    val high: Float,
    val veryHigh: Float,
    val average: Float
)

data class WidgetSnapshot(
    val now: Long,
    val current: CurrentDisplaySource.Snapshot?,
    val history: List<GlucosePoint>,
    val dataState: DisplayDataState.Status,
    val sensorSerial: String?,
    val viewMode: Int,
    val hasCalibration: Boolean,
    val statusText: String?,
    val delta: Float?,
    val range: WidgetRange,
    val isMmol: Boolean
) {
    val hasFreshReading: Boolean get() = current != null && dataState.isFresh
    val valueText: String get() = current?.primaryStr ?: "—"
    val rate: Float get() = current?.rate ?: Float.NaN
    val unitText: String get() = if (isMmol) "mmol/L" else "mg/dL"

    fun stats(period: WidgetStatsPeriod): WidgetRangeStats? {
        val from = if (period == WidgetStatsPeriod.TODAY) {
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else now - period.hours * 3_600_000L
        val points = history.filter { it.timestamp >= from && it.value.isFinite() && it.value > 0.1f }
        if (points.isEmpty()) return null
        var veryLow = 0; var low = 0; var inRange = 0; var high = 0; var veryHigh = 0
        var sum = 0.0
        points.forEach { point ->
            val v = point.value
            sum += v
            when {
                v < range.veryLow -> veryLow++
                v < range.low -> low++
                v <= range.high -> inRange++
                v <= range.veryHigh -> high++
                else -> veryHigh++
            }
        }
        val n = points.size.toFloat()
        return WidgetRangeStats(veryLow/n, low/n, inRange/n, high/n, veryHigh/n, (sum/points.size).toFloat())
    }
}

object WidgetDataSource {
    private fun thresholds(): WidgetRange {
        val fallback = if (Applic.unit == 1) WidgetRange(3.0f, 3.9f, 10.0f, 13.9f)
        else WidgetRange(54f, 70f, 180f, 250f)
        return try {
            val low = Natives.targetlow()
            val high = Natives.targethigh()
            val margin = if (Applic.unit == 1) 1.0f else 18.0f
            val veryLow = if (Natives.hasalarmverylow()) Natives.alarmverylow() else low - margin
            val veryHigh = if (Natives.hasalarmveryhigh()) Natives.alarmveryhigh() else high + margin
            WidgetRange(veryLow, low, high, veryHigh)
        } catch (_: Throwable) { fallback }
    }

    fun load(context: Context, historyMillis: Long): WidgetSnapshot {
        val now = System.currentTimeMillis()
        val serial = WidgetDisplaySource.resolveActiveSensorSerial()
        val current = WidgetDisplaySource.resolveWidgetSnapshot(Notify.glucosetimeout, serial)
        val history = WidgetDisplaySource.resolveChartHistory(current, historyMillis)
        val display = WidgetDisplaySource.resolveDisplaySnapshot(current, history, serial)
        val state = WidgetDisplaySource.resolveDataState(display, history, serial)
        val viewMode = display?.viewMode ?: WidgetDisplaySource.resolveViewMode(serial)
        val status = statusText(context, serial, state)
        return WidgetSnapshot(
            now = now,
            current = display,
            history = history,
            dataState = state,
            sensorSerial = serial,
            viewMode = viewMode,
            hasCalibration = WidgetDisplaySource.hasCalibration(serial, viewMode),
            statusText = status,
            delta = delta(display, history),
            range = thresholds(),
            isMmol = Applic.unit == 1
        )
    }

    private fun delta(current: CurrentDisplaySource.Snapshot?, history: List<GlucosePoint>): Float? {
        val point = current ?: return null
        val previous = history.asReversed().firstOrNull {
            it.timestamp < point.timeMillis - 30_000L &&
                it.timestamp >= point.timeMillis - 10 * 60_000L &&
                it.value.isFinite() && it.value > 0.1f
        } ?: return null
        val value = point.primaryValue
        return if (value.isFinite()) value - previous.value else null
    }

    fun statusText(context: Context, serial: String?, state: DisplayDataState.Status): String? =
        tk.glucodata.WidgetStatusText.resolve(context, serial, state)
}
