package tk.glucodata

import android.content.Context
import java.util.Date

/**
 * Shared home-widget sensor status text.
 *
 * Keep this in main/ so the legacy Java widget, the Glance widget and the
 * redesigned mobile widgets all use exactly the same stale/warm-up/ended logic.
 */
object WidgetStatusText {
    @JvmStatic
    fun resolve(
        context: Context,
        serial: String?,
        state: DisplayDataState.Status
    ): String? {
        if (state.isFresh) return null

        if (serial.isNullOrBlank()) {
            if (state.isStale && state.latestTimestampMillis > 0L) {
                return context.getString(R.string.nonewvalue) +
                    android.text.format.DateFormat.getTimeFormat(context)
                        .format(Date(state.latestTimestampMillis))
            }
            return context.getString(R.string.novalue)
        }

        val nativeStatus = runCatching {
            Natives.getSensorStatusByName(serial).orEmpty()
        }.getOrDefault("")

        if (
            nativeStatus.contains("ended", ignoreCase = true) ||
            nativeStatus.contains("finished", ignoreCase = true) ||
            nativeStatus.contains("zako", ignoreCase = true)
        ) {
            return context.getString(R.string.widget_sensor_finished)
        }

        val dataPtr = runCatching { Natives.getdataptr(serial) }.getOrDefault(0L)
        if (dataPtr != 0L) {
            try {
            val kind = runCatching { Natives.getLibreVersion(dataPtr) }.getOrDefault(-1)
            val start = runCatching { Natives.getSensorStartmsec(dataPtr) }.getOrDefault(0L)
            val warmupMinutes = runCatching {
                Natives.getSensorWarmupMinutes(dataPtr)
            }.getOrDefault(0)

            if ((kind == 2 || kind == 3) && start > 0L && warmupMinutes > 0) {
                val remaining =
                    warmupMinutes * 60_000L - (System.currentTimeMillis() - start)
                if (remaining > 0L) {
                    val minutes = ((remaining + 59_999L) / 60_000L)
                        .toInt()
                        .coerceAtLeast(1)
                    return context.getString(R.string.status_ready_in_minutes, minutes)
                }
            }
            } finally {
                runCatching { Natives.freedataptr(dataPtr) }
                    .onFailure { Log.stack("WidgetStatusText", "free sensor status pointer", it) }
            }
        }

        if (state.isStale && state.latestTimestampMillis > 0L) {
            return context.getString(R.string.nonewvalue) +
                android.text.format.DateFormat.getTimeFormat(context)
                    .format(Date(state.latestTimestampMillis))
        }

        return nativeStatus.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.novalue)
    }
}
