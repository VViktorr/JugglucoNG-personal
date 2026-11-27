package tk.glucodata.ui.stats

import tk.glucodata.ui.GlucosePoint
import java.time.Instant
import java.time.ZoneId

/** Report-only 15-minute bins. Do not relabel the screen's P10/P90 as P5/P95. */
internal data class AgpReportBin(
    val index: Int,
    val p05: Float,
    val p25: Float,
    val median: Float,
    val p75: Float,
    val p95: Float
)

internal object AgpReportProfile {
    fun build(points: List<GlucosePoint>, zone: ZoneId): List<AgpReportBin> {
        val bins = Array(96) { mutableListOf<Float>() }
        points.forEach { point ->
            if (point.value.isFinite() && point.value > 0f) {
                val time = Instant.ofEpochMilli(point.timestamp).atZone(zone)
                bins[time.hour * 4 + time.minute / 15].add(point.value)
            }
        }
        return bins.mapIndexedNotNull { index, values ->
            if (values.isEmpty()) null else {
                values.sort()
                AgpReportBin(
                    index,
                    StatsAnalytics.percentile(values, 0.05f),
                    StatsAnalytics.percentile(values, 0.25f),
                    StatsAnalytics.percentile(values, 0.50f),
                    StatsAnalytics.percentile(values, 0.75f),
                    StatsAnalytics.percentile(values, 0.95f)
                )
            }
        }
    }

    /** Empty clock bins stay empty instead of connecting across missing data. */
    fun segments(bins: List<AgpReportBin>): List<List<AgpReportBin>> {
        val result = mutableListOf<MutableList<AgpReportBin>>()
        bins.forEach { bin ->
            if (result.isEmpty() || result.last().last().index + 1 != bin.index) {
                result.add(mutableListOf())
            }
            result.last().add(bin)
        }
        return result
    }
}
