package tk.glucodata.ui.stats

import androidx.annotation.StringRes
import tk.glucodata.R
import java.time.LocalDate
import java.time.YearMonth

enum class LibreViewReportGroup(val key: String, @param:StringRes val labelResId: Int) {
    AGP("agp", R.string.lv_report_title),
    GLUCOSE_INSIGHTS("glucose_insights", R.string.lv_group_insights),
    SNAPSHOT("snapshot", R.string.lv_report_snapshot),
    DAILY_TRENDS("daily_trends", R.string.lv_report_daily_trends),
    WEEKLY_SUMMARY("weekly_summary", R.string.lv_group_weekly),
    MONTHLY_SUMMARY("monthly_summary", R.string.lv_group_monthly),
    DAILY_LOG("daily_log", R.string.lv_group_daily_log),
    MEAL_PATTERNS("meal_patterns", R.string.lv_group_meals),
    DEVICE("device", R.string.lv_group_device);

    companion object {
        val defaults: Set<LibreViewReportGroup> = setOf(AGP, SNAPSHOT, DAILY_TRENDS)

        fun fromKeys(keys: Collection<String>?): Set<LibreViewReportGroup> =
            if (keys == null) defaults else entries.filterTo(linkedSetOf()) { it.key in keys }
    }
}

internal data class LibreViewReportPage(
    val group: LibreViewReportGroup,
    val dates: List<LocalDate> = emptyList(),
    val month: YearMonth? = null
)

/** Calculate pages before opening the document so every header has the true total. */
internal object LibreViewReportPages {
    fun plan(groups: Set<LibreViewReportGroup>, first: LocalDate, last: LocalDate): List<LibreViewReportPage> {
        require(groups.isNotEmpty()) { "Choose at least one report group" }
        require(!last.isBefore(first))
        val dates = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
        return LibreViewReportGroup.entries.filter { it in groups }.flatMap { group ->
            when (group) {
                LibreViewReportGroup.WEEKLY_SUMMARY -> dates.chunked(7).map { LibreViewReportPage(group, it) }
                LibreViewReportGroup.MONTHLY_SUMMARY -> dates.map { YearMonth.from(it) }.distinct()
                    .map { LibreViewReportPage(group, month = it) }
                LibreViewReportGroup.DAILY_LOG -> dates.chunked(3).map { LibreViewReportPage(group, it) }
                LibreViewReportGroup.MEAL_PATTERNS -> dates.chunked(14).map { LibreViewReportPage(group, it) }
                else -> listOf(LibreViewReportPage(group))
            }
        }
    }
}
