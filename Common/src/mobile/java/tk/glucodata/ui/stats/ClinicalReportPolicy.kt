package tk.glucodata.ui.stats

import java.time.Instant
import java.time.ZoneId

/** Export policy only. Never writes the user's targets or alarm settings. */
internal object ClinicalReportPolicy {
    fun range(days: Int, now: Long, zone: ZoneId): StatsDateRange {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return StatsDateRange(today.minusDays(days.coerceAtLeast(1).toLong() - 1L)
            .atStartOfDay(zone).toInstant().toEpochMilli(), now)
    }

    // Bergenstal et al., Diabetes Care 2018, doi:10.2337/dc18-1581.
    fun gmiMmolMol(meanMgDl: Float): Float = 12.71f + 4.70587f * (meanMgDl / 18.0182f)

    /** Missing treatment days are not zero-dose days. */
    fun recordedDailyMean(entries: List<Pair<Long, Float>>, zone: ZoneId): Float? {
        val valid = entries.filter { it.second.isFinite() && it.second > 0f }
        if (valid.isEmpty()) return null
        val recordedDays = valid.map { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }.distinct().size
        return (valid.sumOf { it.second.toDouble() } / recordedDays).toFloat()
    }
}
