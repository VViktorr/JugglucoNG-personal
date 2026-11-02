package tk.glucodata.ui.stats

import tk.glucodata.data.journal.JournalEntryEntity
import tk.glucodata.ui.GlucosePoint
import kotlin.math.abs

internal data class LibreViewMealSample(val timestamp: Long, val glucose: List<Float?>)

internal object LibreViewMealProfile {
    /** Actual carbohydrate entries anchor meals; insulin injections are not meal evidence. */
    fun build(points: List<GlucosePoint>, journal: List<JournalEntryEntity>): List<LibreViewMealSample> {
        val history = points.filter { it.value.isFinite() && it.value > 0f }.sortedBy { it.timestamp }
        fun lowerBound(time: Long): Int {
            var low = 0
            var high = history.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (history[mid].timestamp < time) low = mid + 1 else high = mid
            }
            return low
        }
        fun reading(time: Long, before: Boolean): Float? {
            val index = lowerBound(time)
            return listOfNotNull(history.getOrNull(index - 1), history.getOrNull(index))
                .filter { (!before || it.timestamp <= time) && abs(it.timestamp - time) <= 15 * 60_000L }
                .minByOrNull { abs(it.timestamp - time) }?.value
        }
        return journal.filter {
            it.entryType == "carbs" && it.amount?.let { value -> value.isFinite() && value > 0f } == true
        }.map { entry ->
            LibreViewMealSample(entry.timestamp, listOf(
                reading(entry.timestamp, true),
                reading(entry.timestamp + 60 * 60_000L, false),
                reading(entry.timestamp + 120 * 60_000L, false),
                reading(entry.timestamp + 180 * 60_000L, false)
            ))
        }
    }
}
