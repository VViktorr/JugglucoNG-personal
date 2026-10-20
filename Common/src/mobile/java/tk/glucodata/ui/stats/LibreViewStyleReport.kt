package tk.glucodata.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import tk.glucodata.R
import tk.glucodata.data.HistoryDatabase
import tk.glucodata.data.journal.JournalEntryEntity
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.util.GlucoseFormatter
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Reference-shaped reports; never imports values, identifiers or branding from a PDF. */
internal object LibreViewStyleReport {
    suspend fun write(context: Context, uri: Uri, state: StatsUiState, reportDays: Int,
                      patient: StatsReportExporter.PatientInfo?, groups: Set<LibreViewReportGroup>) {
        require(groups.isNotEmpty()) { context.getString(R.string.lv_groups_choose) }
        val end = state.activeRange?.endMillis ?: System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val start = state.activeRange?.startMillis ?: ClinicalReportPolicy.range(reportDays, end, zone).startMillis
        val points = state.readings.filter { it.timestamp in start..end && it.value.isFinite() && it.value > 0f }.sortedBy { it.timestamp }
        require(points.isNotEmpty() || groups == setOf(LibreViewReportGroup.DEVICE)) { context.getString(R.string.lv_report_no_data) }
        val journalGroups = setOf(LibreViewReportGroup.SNAPSHOT, LibreViewReportGroup.DAILY_TRENDS,
            LibreViewReportGroup.WEEKLY_SUMMARY, LibreViewReportGroup.DAILY_LOG, LibreViewReportGroup.MEAL_PATTERNS)
        val entries = if (groups.any { it in journalGroups }) HistoryDatabase.getInstance(context)
            .journalDao().getEntriesBetween(start, end) else emptyList()
        val pages = LibreViewReportPages.plan(groups, Instant.ofEpochMilli(start).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(end).atZone(zone).toLocalDate())
        val renderer = Renderer(context, state, points, entries, start, end, patient?.sanitized())
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, spec ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(LibreViewReportLayout.WIDTH, LibreViewReportLayout.HEIGHT, index + 1).create())
                val groupPages = pages.filter { it.group == spec.group }
                try {
                    renderer.page(page.canvas, spec, index, pages.size, groupPages.indexOf(spec), groupPages.size)
                } finally { document.finishPage(page) }
            }
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IOException("Unable to open destination file")
        } finally { document.close() }
    }

    private class Renderer(val context: Context, val state: StatsUiState, val points: List<GlucosePoint>,
                           val journal: List<JournalEntryEntity>, val start: Long, val end: Long,
                           val patient: StatsReportExporter.PatientInfo?) {
        private val zone = ZoneId.systemDefault()
        private val locale = Locale.getDefault()
        private val summary = state.summary
        private val targets = state.targets
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun font(id: Int, bold: Boolean = false): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
                ?: Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        private val interRegular = font(R.font.report_inter_regular)
        private val interBold = font(R.font.report_inter_bold, true)
        private val legacyRegular = font(R.font.report_arial_compatible_regular)
        private val legacyBold = font(R.font.report_arial_compatible_bold, true)
        private var regularFont = interRegular
        private var boldFont = interBold
        private val ink = Color.rgb(25, 29, 36)
        private val gray = Color.rgb(118, 121, 129)
        private val border = Color.rgb(167, 170, 177)
        private val cyan = Color.rgb(38, 148, 197)
        private val blue = Color.rgb(0, 103, 172)
        private val green = Color.rgb(82, 145, 69)
        private val orange = Color.rgb(238, 156, 0)
        private val red = Color.rgb(220, 25, 60)
        private val pale = Color.rgb(244, 244, 245)
        private val unit = if (state.unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
        private val bins = AgpReportProfile.build(points, zone)
        private val daily = points.groupBy { date(it.timestamp) }
        private val journalByDay = journal.groupBy { date(it.timestamp) }
        private val dayDetails by lazy { StatsAnalytics.dayBreakdowns(points, targets).associateBy { it.date } }
        private val calendarPoints = state.reportCalendarReadings.ifEmpty { points }
        private val calendarDetails by lazy { StatsAnalytics.dayBreakdowns(calendarPoints, targets).associateBy { it.date } }
        private val calendarLows by lazy { StatsAnalytics.detectEpisodes(calendarPoints, targets)
            .filter { it.kind == EpisodeKind.LOW }.groupingBy { date(it.startMillis) }.eachCount() }
        private val lows by lazy { summary.episodes.filter { it.kind == EpisodeKind.LOW }.groupingBy { date(it.startMillis) }.eachCount() }
        private val meals by lazy { LibreViewMealProfile.build(points, journal) }
        private val gapLimit = (StatsAnalytics.estimateCadenceMillis(points) * 3L).coerceAtMost(15 * 60_000L)
        private val dayCount = (ChronoUnit.DAYS.between(date(start), date(end)) + 1L).coerceAtLeast(1L).toInt()
        private val recent = (0 until minOf(dayCount, 14)).map { date(end).minusDays(it.toLong()) }.reversed()
        private val period get() = "$dayCount ${s(R.string.lv_match_days)} ${shortDate(date(start))} - ${shortDate(date(end))}"
        private fun s(@StringRes id: Int) = context.getString(id)
        private fun date(time: Long) = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        private fun shortDate(day: LocalDate) = day.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        private fun glucose(value: Float) = GlucoseFormatter.formatFromMgDl(value, state.unit == GlucoseUnit.MMOL)
        private fun number(value: Float, decimals: Int = 1) = String.format(locale, "%.${decimals}f", value)
        private fun pct(value: Float) = "${value.roundToInt()}%"
        private fun frame(key: String) = LibreViewReportLayout.frame(key)
        private fun valid(e: JournalEntryEntity) = e.amount?.let { it.isFinite() && it > 0f } == true
        private fun amounts(type: String) = journal.filter { it.entryType == type && valid(it) }
        private fun recordedDays(type: String) = amounts(type).map { date(it.timestamp) }.distinct().size
        private fun recordedAverage(type: String): String {
            val entries = amounts(type)
            return ClinicalReportPolicy.recordedDailyMean(entries.map { it.timestamp to it.amount!! }, zone)?.let { number(it) } ?: "-"
        }

        private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 8f,
                         bold: Boolean = false, color: Int = ink, width: Float = 536f) {
            paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = color
            paint.typeface = if (bold) boldFont else regularFont
            paint.textSize = size
            val shown = if (paint.measureText(value) <= width) value else {
                val count = paint.breakText(value, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
                value.take(count) + "…"
            }
            c.drawText(shown, x, y, paint)
        }
        private fun right(c: Canvas, value: String, x: Float, y: Float, size: Float = 8f,
                          bold: Boolean = false, color: Int = ink, width: Float = 100f) {
            paint.typeface = if (bold) boldFont else regularFont; paint.textSize = size
            text(c, value, x - minOf(width, paint.measureText(value)), y, size, bold, color, width)
        }
        private fun wrap(c: Canvas, value: String, x: Float, top: Float, width: Float,
                         size: Float = 7f, maxLines: Int = 3, color: Int = ink): Float {
            var rest = value.trim(); var y = top
            repeat(maxLines) { index ->
                if (rest.isEmpty()) return y
                paint.typeface = regularFont; paint.textSize = size
                var n = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                if (n < rest.length) { val split = rest.lastIndexOf(' ', n - 1); if (split > 0) n = split }
                text(c, if (index == maxLines - 1) rest else rest.take(n), x, y, size, color = color, width = width)
                rest = rest.drop(n).trimStart(); y += size + 2f
            }
            return y
        }
        private fun fill(c: Canvas, r: RectF, color: Int) { paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = color; c.drawRect(r, paint) }
        private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int = border,
                         width: Float = 0.4f, dashed: Boolean = false) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
            paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(2f, 2f), 0f) else null
            c.drawLine(x1, y1, x2, y2, paint); paint.pathEffect = null
        }
        private fun outline(c: Canvas, r: RectF, radius: Float = 0f, color: Int = border) {
            paint.style = Paint.Style.STROKE; paint.pathEffect = null; paint.strokeWidth = 0.5f; paint.color = color
            c.drawRoundRect(r, radius, radius, paint)
        }
        private fun card(c: Canvas, key: String, title: String) {
            val r = frame(key); outline(c, r, 6f)
            text(c, title, r.left + 9f, r.top + 14f, 8f, true, width = r.width() - 18f)
        }
        private fun ruleTitle(c: Canvas, title: String, x: Float, y: Float, width: Float, color: Int = ink) {
            text(c, title, x, y, 9f, true, color, width); line(c, x, y + 5f, x + width, y + 5f, gray, 0.6f)
        }

        fun page(c: Canvas, spec: LibreViewReportPage, index: Int, total: Int, groupIndex: Int, groupTotal: Int) {
            c.drawColor(Color.WHITE)
            val modern = spec.group in setOf(LibreViewReportGroup.AGP, LibreViewReportGroup.GLUCOSE_INSIGHTS,
                LibreViewReportGroup.DAILY_LOG, LibreViewReportGroup.DEVICE)
            regularFont = if (modern) interRegular else legacyRegular
            boldFont = if (modern) interBold else legacyBold
            if (modern) {
                text(c, patient?.name.orEmpty().ifBlank { "JugglucoNG" }, 38f, 48f, 10f, width = 370f)
                text(c, patient?.dateOfBirth.orEmpty().let { if (it.isBlank()) "" else "${s(R.string.patient_dob_label)}: $it" }, 38f, 61f, 7f, width = 370f)
                right(c, "JugglucoNG", 574f, 54f, 14f, true, width = 160f)
                text(c, s(spec.group.labelResId), 38f, 94f, 18f, true, width = 536f)
                fill(c, RectF(38f, 110f, if (spec.group == LibreViewReportGroup.DAILY_LOG) 574f else 302f, 128f), pale)
                text(c, period, 45f, 122f, 8f, width = 520f)
            } else {
                text(c, s(spec.group.labelResId), 66f, 70f, 15f, true, cyan, 400f)
                text(c, period, 66f, 83f, 7f, color = gray, width = 490f)
                right(c, "JugglucoNG", 584f, 70f, 18f, true, cyan, 140f)
                val save = c.save(); c.rotate(-90f)
                text(c, patient?.name.orEmpty(), -640f, 40f, 8f, width = 180f)
                text(c, patient?.dateOfBirth.orEmpty(), -640f, 49f, 6f, width = 180f)
                text(c, "JugglucoNG", -410f, 40f, 8f, width = 180f)
                c.restoreToCount(save)
            }
            when (spec.group) {
                LibreViewReportGroup.AGP -> overview(c)
                LibreViewReportGroup.GLUCOSE_INSIGHTS -> insights(c)
                LibreViewReportGroup.SNAPSHOT -> snapshot(c)
                LibreViewReportGroup.DAILY_TRENDS -> trends(c)
                LibreViewReportGroup.WEEKLY_SUMMARY -> weekly(c, spec.dates)
                LibreViewReportGroup.MONTHLY_SUMMARY -> monthly(c, checkNotNull(spec.month))
                LibreViewReportGroup.DAILY_LOG -> dailyLog(c, spec.dates)
                LibreViewReportGroup.MEAL_PATTERNS -> mealPage(c, spec.dates)
                LibreViewReportGroup.DEVICE -> device(c)
            }
            text(c, "${s(R.string.lv_match_generated)} ${date(System.currentTimeMillis()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))}", 38f, 746f, 7f)
            right(c, "${s(R.string.lv_match_page)} ${groupIndex + 1}/$groupTotal  (${index + 1}/$total)", 574f, 746f, 7f, width = 180f)
            text(c, s(R.string.lv_match_method), 38f, 758f, 5.5f, color = gray, width = 536f)
        }

        private fun rangeCards(c: Canvas, insights: Boolean = false) {
            fill(c, RectF(310f, 110f, 574f, 128f), pale)
            text(c, "${s(R.string.lv_report_coverage)} ${pct(summary.coverage.percent)}", 317f, 122f, 8f, width = 250f)
            card(c, "agp.ranges", s(R.string.lv_match_time_ranges))
            text(c, s(R.string.lv_match_standard_targets), 187f, 151f, 5f, color = gray, width = 107f)
            val colors = listOf(orange, Color.rgb(255, 220, 0), green, red, Color.rgb(165, 0, 35))
            val values = listOf(summary.tir.veryHighPercent, summary.tir.highPercent, summary.tir.inRangePercent, summary.tir.lowPercent, summary.tir.veryLowPercent)
            val labels = listOf(R.string.lv_report_very_high, R.string.lv_report_high, R.string.lv_report_target, R.string.lv_report_low, R.string.lv_report_very_low)
            var top = 166f
            values.forEachIndexed { i, p -> val h = p * 1.42f; fill(c, RectF(63f, top, 104f, top + h), colors[i]); top += h }
            outline(c, RectF(62f, 165f, 105f, 309f), 3f)
            listOf(targets.veryHighMgDl to 175f, targets.highMgDl to 201f,
                targets.lowMgDl to 294f, targets.veryLowMgDl to 305f).forEach { (value, y) ->
                right(c, glucose(value), 60f, y, 5.5f, color = gray, width = 22f)
            }
            val ys = listOf(171f, 202f, 239f, 278f, 298f)
            values.forEachIndexed { i, value ->
                text(c, s(labels[i]), 114f, ys[i], 7f, width = 83f)
                if (i != 2) right(c, pct(value), 193f, ys[i], 8f, true, width = 45f)
            }
            text(c, "${glucose(targets.lowMgDl)} - ${glucose(targets.highMgDl)} ($unit)", 114f, 250f, 6.5f, width = 105f)
            listOf(211f, 261f).forEach { line(c, 108f, it, 302f, it) }
            right(c, pct(summary.tir.aboveRangePercent), 295f, 190f, 14f, true, width = 70f)
            right(c, pct(summary.tir.inRangePercent), 295f, 241f, 14f, true, width = 70f)
            right(c, pct(summary.tir.belowRangePercent), 295f, 291f, 14f, true, width = 70f)
            fun goal(value: String, x: Float, y: Float, width: Float = 51f) {
                fill(c, RectF(x, y - 7f, x + width, y + 3f), pale)
                text(c, "${s(R.string.lv_match_goal)} $value", x + 2f, y, 6f, width = width - 4f)
            }
            goal("< 5%", 114f, 184f, 44f)
            goal("< 1%", 114f, 309f, 44f)
            goal("< 25%", 200f, 188f)
            goal("> 70%", 200f, 239f)
            goal("< 4%", 200f, 289f)
            card(c, "agp.metrics", s(R.string.lv_match_metrics))
            val meanY = if (insights) 198f else 185f
            text(c, s(R.string.lv_report_mean), 318f, meanY - 10f, 8f, width = 155f)
            right(c, "${glucose(summary.avgMgDl)} $unit", 566f, meanY + 4f, 13f, true, width = 115f)
            goal("< ${glucose(154f)} $unit", 318f, meanY + 13f, 92f)
            line(c, 310f, if (insights) 237f else 207f, 574f, if (insights) 237f else 207f)
            val gmiY = if (insights) 289f else 241f
            text(c, s(R.string.lv_match_gmi), 318f, gmiY - 18f, 8f, width = 245f)
            text(c, "${number(summary.gmiPercent)}%", 318f, gmiY, 14f, true)
            text(c, "${ClinicalReportPolicy.gmiMmolMol(summary.avgMgDl).roundToInt()} mmol/mol", 439f, gmiY, 11f, true, width = 125f)
            goal("< 7%", 366f, gmiY, 62f)
            if (!insights) {
                line(c, 310f, 254f, 574f, 254f)
                text(c, s(R.string.lv_report_variability), 318f, 274f, 8f, width = 180f)
                right(c, "${number(summary.cvPercent)}%", 566f, 289f, 14f, true, width = 95f)
                goal("< 36%", 318f, 303f, 63f)
            }
        }

        private fun overview(c: Canvas) {
            rangeCards(c)
            card(c, "agp.profile", s(R.string.lv_report_profile))
            wrap(c, s(R.string.lv_match_agp_explanation), 47f, 345f, 518f, 7.5f, 2)
            profile(c, frame("agp.plot"), modern = true)
            val rows = (recent.size + 6) / 7
            val dailyCard = frame("agp.daily").apply { bottom += (rows - 1) * 48f }
            outline(c, dailyCard, 6f)
            text(c, s(R.string.lv_report_daily_profiles), 47f, 549f, 8f, true)
            text(c, s(R.string.lv_match_daily_explanation), 47f, 561f, 7f, width = 518f)
            recent.forEachIndexed { i, day ->
                val x = 74f + (i % 7) * (488f / 7f); val y = 587f + (i / 7) * 48f
                text(c, day.dayOfWeek.getDisplayName(TextStyle.FULL, locale), x, y - 8f, 6.5f, width = 67f)
                trace(c, day, RectF(x, y, x + 488f / 7f, y + 39f), modern = true, compact = true)
                text(c, day.dayOfMonth.toString(), x + 3f, y + 10f, 6f)
                text(c, "12:00", x + 23f, y + 48f, 5.5f, color = gray, width = 30f)
            }
        }

        private fun insights(c: Canvas) {
            rangeCards(c, true)
            card(c, "insights.notes", s(R.string.lv_groups_observations))
            summary.insights.take(3).forEachIndexed { i, item ->
                text(c, item.title, 51f, 351f + i * 35f, 8f, true, width = 510f)
                wrap(c, item.message, 51f, 363f + i * 35f, 510f, 7f, 2)
            }
            if (summary.insights.isEmpty()) text(c, s(R.string.lv_groups_no_observations), 51f, 356f, 8f, width = 510f)
            card(c, "insights.profile", s(R.string.lv_report_daily_trends))
            profile(c, frame("insights.plot"), modern = true)
            listOf(R.string.lv_groups_night, R.string.lv_groups_morning, R.string.lv_groups_midday, R.string.lv_groups_evening)
                .forEachIndexed { i, title -> text(c, s(title), 130f + i * 102f, 667f, 7f, color = gray, width = 100f) }
        }

        private fun valueY(value: Float, r: RectF, max: Float = 350f) = r.bottom - value.coerceIn(0f, max) / max * r.height()
        private fun clockX(timestamp: Long, r: RectF): Float {
            val t = Instant.ofEpochMilli(timestamp).atZone(zone)
            return r.left + (t.hour * 60f + t.minute + t.second / 60f) / 1440f * r.width()
        }
        private fun axes(c: Canvas, r: RectF, max: Float = 350f, interval: Int = 3,
                         modern: Boolean = false, compact: Boolean = false, timesTop: Boolean = false,
                         timeLabelY: Float? = null) {
            val ticks = if (compact) listOf(0f, targets.lowMgDl, targets.highMgDl, max)
                else listOf(0f, targets.veryLowMgDl, targets.lowMgDl, targets.highMgDl, targets.veryHighMgDl, max)
            val printed = mutableListOf<Float>()
            ticks.distinct().sortedBy { if (it == targets.lowMgDl || it == targets.highMgDl) 0 else 1 }.forEach { tick ->
                val y = valueY(tick, r, max); val target = tick == targets.lowMgDl || tick == targets.highMgDl
                line(c, r.left, y, r.right, y, if (target && modern) green else border, if (target) 0.7f else 0.35f, !target)
                if (printed.none { abs(it - y) < if (modern) 5.5f else 8f }) {
                    if (target && modern) {
                        fill(c, RectF(r.left - 69f, y - 5f, r.left - 49f, y + 5f), green)
                        text(c, glucose(tick), r.left - 67f, y + 2f, 6f, true, Color.WHITE, 19f)
                    } else right(c, glucose(tick), r.left - 6f, y + 2f, if (compact) 5.5f else 6.5f, color = gray, width = 35f)
                    printed.add(y)
                }
            }
            for (hour in 0..24 step interval) {
                val x = r.left + hour / 24f * r.width()
                line(c, x, r.top, x, r.bottom, border, 0.35f, true)
                text(c, "%02d:00".format(locale, hour % 24), x - 11f,
                    timeLabelY ?: if (timesTop) r.top - 5f else r.bottom + 12f, if (compact) 5.5f else 6.5f, color = gray, width = 29f)
            }
            outline(c, r)
        }
        private fun curvePath(values: List<Pair<Float, Float>>, path: Path = Path(), move: Boolean = true): Path {
            if (values.isEmpty()) return path
            val first = values.first(); if (move) path.moveTo(first.first, first.second) else path.lineTo(first.first, first.second)
            for (i in 1..values.lastIndex) {
                val previous = values[i - 1]; val current = values[i]
                path.quadTo(previous.first, previous.second, (previous.first + current.first) / 2f, (previous.second + current.second) / 2f)
            }
            path.lineTo(values.last().first, values.last().second); return path
        }
        private fun profile(c: Canvas, r: RectF, modern: Boolean = false, compact: Boolean = false, interval: Int = 3,
                            timesTop: Boolean = false, legend: Boolean = true, timeLabelY: Float? = null) {
            fill(c, RectF(r.left, valueY(targets.highMgDl, r), r.right, valueY(targets.lowMgDl, r)), Color.rgb(239, 240, 241))
            val saved = c.save(); c.clipRect(r)
            fun x(bin: AgpReportBin) = r.left + (bin.index + 0.5f) / 96f * r.width()
            fun band(segment: List<AgpReportBin>, lower: (AgpReportBin) -> Float, upper: (AgpReportBin) -> Float, color: Int) {
                val path = curvePath(segment.map { x(it) to valueY(upper(it), r) })
                curvePath(segment.asReversed().map { x(it) to valueY(lower(it), r) }, path, false); path.close()
                paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = color; c.drawPath(path, paint)
            }
            AgpReportProfile.segments(bins).forEach { segment ->
                band(segment, { it.p05 }, { it.p95 }, if (modern) Color.rgb(213, 236, 204) else Color.rgb(220, 227, 239))
                band(segment, { it.p25 }, { it.p75 }, if (modern) Color.rgb(151, 199, 132) else Color.rgb(156, 178, 209))
                if (modern) {
                    val above = c.save(); c.clipRect(r.left, r.top, r.right, valueY(targets.highMgDl, r))
                    band(segment, { it.p05 }, { it.p95 }, Color.rgb(255, 242, 172))
                    band(segment, { it.p25 }, { it.p75 }, Color.rgb(255, 219, 61)); c.restoreToCount(above)
                    val below = c.save(); c.clipRect(r.left, valueY(targets.lowMgDl, r), r.right, r.bottom)
                    band(segment, { it.p05 }, { it.p95 }, Color.rgb(252, 218, 223))
                    band(segment, { it.p25 }, { it.p75 }, Color.rgb(244, 147, 163)); c.restoreToCount(below)
                }
                val path = curvePath(segment.map { x(it) to valueY(it.median, r) })
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.15f; paint.color = if (modern) green else blue; c.drawPath(path, paint)
                if (segment.size == 1) {
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x(segment.first()), valueY(segment.first().median, r), 1f, paint)
                }
            }
            c.restoreToCount(saved)
            axes(c, r, interval = interval, modern = modern, compact = compact, timesTop = timesTop, timeLabelY = timeLabelY)
            text(c, unit, if (compact) r.left - 20f else r.left - 29f,
                if (compact) r.top - 9f else r.top + 12f, 6f, color = gray, width = 28f)
            if (legend) {
                val last = bins.lastOrNull()
                if (last != null) {
                    val values = listOf(last.p95, last.p75, last.median, last.p25, last.p05)
                    val ys = mutableListOf<Float>()
                    values.forEach { value -> ys.add(maxOf(valueY(value, r), (ys.lastOrNull() ?: (r.top - 8f)) + 8f)) }
                    val overflow = ((ys.lastOrNull() ?: r.bottom) - r.bottom).coerceAtLeast(0f)
                    values.forEachIndexed { i, value ->
                        val y = ys[i] - overflow
                        text(c, listOf("95%", "75%", "50%", "25%", "5%")[i], r.right + 12f, y + 2f, 6.5f, i == 2, gray, 30f)
                        line(c, r.right, valueY(value, r), r.right + 9f, y, gray, 0.3f)
                    }
                }
            }
        }

        private fun trace(c: Canvas, day: LocalDate, r: RectF, modern: Boolean = false, compact: Boolean = false, timesTop: Boolean = false) {
            fill(c, RectF(r.left, valueY(targets.highMgDl, r), r.right, valueY(targets.lowMgDl, r)), Color.rgb(221, 223, 225))
            if (!compact) axes(c, r, interval = if (modern) 2 else 6, modern = false, compact = true, timesTop = timesTop)
            val save = c.save(); c.clipRect(r)
            val data = daily[day].orEmpty(); var previous: GlucosePoint? = null; var previousX = Float.NEGATIVE_INFINITY
            data.forEach { point ->
                val x = clockX(point.timestamp, r); val y = valueY(point.value, r)
                val old = previous
                if (old != null && point.timestamp - old.timestamp <= gapLimit && x >= previousX) {
                    fun excursion(top: Float, bottom: Float, threshold: Float, color: Int) {
                        val clip = c.save(); c.clipRect(r.left, top, r.right, bottom)
                        val path = Path(); path.moveTo(previousX, valueY(old.value, r)); path.lineTo(x, y)
                        path.lineTo(x, valueY(threshold, r)); path.lineTo(previousX, valueY(threshold, r)); path.close()
                        paint.style = Paint.Style.FILL; paint.color = color; c.drawPath(path, paint); c.restoreToCount(clip)
                    }
                    excursion(r.top, valueY(targets.highMgDl, r), targets.highMgDl, Color.rgb(255, 224, 59))
                    excursion(valueY(targets.lowMgDl, r), r.bottom, targets.lowMgDl, Color.rgb(252, 189, 202))
                    line(c, previousX, valueY(old.value, r), x, y, if (modern) green else blue, 0.7f)
                    val over = c.save(); c.clipRect(r.left, r.top, r.right, valueY(targets.highMgDl, r))
                    line(c, previousX, valueY(old.value, r), x, y, orange, 0.8f); c.restoreToCount(over)
                    val under = c.save(); c.clipRect(r.left, valueY(targets.lowMgDl, r), r.right, r.bottom)
                    line(c, previousX, valueY(old.value, r), x, y, red, 0.8f); c.restoreToCount(under)
                } else {
                    paint.style = Paint.Style.FILL; paint.color = if (modern) green else blue
                    c.drawCircle(x, y, 0.65f, paint)
                }
                previous = point; previousX = x
            }
            c.restoreToCount(save)
            if (compact) {
                outline(c, r, 4f)
                if (recent.indexOf(day) % 7 == 0) listOf(targets.lowMgDl, targets.highMgDl).forEach {
                    right(c, glucose(it), r.left - 4f, valueY(it, r) + 2f, 5.5f, width = 25f)
                }
            }
        }

        private fun snapshot(c: Canvas) {
            ruleTitle(c, s(R.string.lv_report_glucose), 66f, 116f, 381f, blue)
            right(c, "GMI ${number(summary.gmiPercent)}% | ${ClinicalReportPolicy.gmiMmolMol(summary.avgMgDl).roundToInt()} mmol/mol", 445f, 115f, 7f, color = blue, width = 190f)
            fun metric(label: String, value: String, y: Float) {
                text(c, label, 66f, y, 7f, width = 106f)
                val mean = label == s(R.string.lv_report_mean)
                right(c, value, if (mean) 190f else 221f, y, if (mean) 14f else 11f, true, width = 65f)
                line(c, 66f, y + 7f, 233f, y + 7f)
            }
            metric(s(R.string.lv_report_mean), glucose(summary.avgMgDl), 159f)
            metric(s(R.string.lv_report_above), pct(summary.tir.aboveRangePercent), 181f)
            metric(s(R.string.lv_report_target), pct(summary.tir.inRangePercent), 203f)
            metric(s(R.string.lv_report_below), pct(summary.tir.belowRangePercent), 225f)
            text(c, s(R.string.lv_report_mean), 244f, 137f, 7f, true)
            profile(c, frame("snapshot.glucose"), compact = true, interval = 6, legend = false)
            metric(s(R.string.lv_report_event_count), summary.lowEpisodes.count.toString(), 310f)
            val lowEpisodes = summary.episodes.filter { it.kind == EpisodeKind.LOW }
            val meanDuration = if (lowEpisodes.isEmpty()) 0 else lowEpisodes.map { it.durationMinutes }.average().roundToInt()
            metric(s(R.string.lv_match_mean_duration), "$meanDuration min", 332f)
            text(c, s(R.string.lv_report_low_events), 244f, 287f, 6.5f, true, width = 201f)
            val lowPlot = frame("snapshot.lows")
            for (v in 40..100 step 10) { val y = lowPlot.bottom - (v - 40) / 60f * lowPlot.height(); line(c, lowPlot.left, y, lowPlot.right, y); right(c, v.toString(), lowPlot.left - 4f, y + 2f, 5.5f, width = 20f) }
            val lowPoints = points.filter { it.value < targets.lowMgDl }.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour * 4 + Instant.ofEpochMilli(it.timestamp).atZone(zone).minute / 15 }
            lowPoints.forEach { (bin, values) ->
                val x = lowPlot.left + bin / 96f * lowPlot.width(); val y = lowPlot.bottom - (values.minOf { it.value } - 40f).coerceIn(0f, 60f) / 60f * lowPlot.height()
                fill(c, RectF(x, lowPlot.bottom - (targets.lowMgDl - 40f) / 60f * lowPlot.height(), x + lowPlot.width() / 96f, y), Color.rgb(250, 188, 197))
            }
            outline(c, lowPlot); text(c, "00:00", lowPlot.left, lowPlot.bottom + 12f, 5.5f, color = gray); right(c, "00:00", lowPlot.right, lowPlot.bottom + 12f, 5.5f)
            ruleTitle(c, s(R.string.lv_report_sensor_use), 66f, 450f, 381f, cyan)
            metric(s(R.string.lv_report_coverage), pct(summary.coverage.percent), 494f)
            wrap(c, s(R.string.lv_match_scans_per_day), 66f, 518f, 130f, 6.5f, 2)
            right(c, "-", 228f, 518f, 11f, true, width = 30f)
            val r = frame("snapshot.coverage")
            text(c, s(R.string.lv_report_coverage), r.left, r.top - 8f, 7f, true, width = 201f)
            val counts = points.groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }.eachCount()
            val cadence = StatsAnalytics.estimateCadenceMillis(points)
            val coverage = (0..23).map { hour ->
                val expectedMillis = (0 until dayCount).sumOf { day ->
                    val local = date(start).plusDays(day.toLong()).atTime(hour, 0)
                    val from = maxOf(start, local.atZone(zone).toInstant().toEpochMilli())
                    val until = minOf(end, local.plusHours(1).atZone(zone).toInstant().toEpochMilli())
                    (until - from).coerceAtLeast(0L)
                }.coerceAtLeast(1L)
                (counts[hour].orEmptyCount() * cadence / expectedMillis.toFloat() * 100f).coerceIn(0f, 100f)
            }
            val path = Path(); path.moveTo(r.left, r.bottom)
            coverage.forEachIndexed { i, value -> path.lineTo(r.left + i / 23f * r.width(), r.bottom - value / 100f * r.height()) }
            path.lineTo(r.right, r.bottom); path.close(); paint.style = Paint.Style.FILL; paint.color = Color.rgb(226, 242, 245); c.drawPath(path, paint)
            outline(c, r); text(c, "100%", r.left - 23f, r.top + 2f, 6f); text(c, "0%", r.left - 17f, r.bottom, 6f)
            text(c, "00:00", r.left, r.bottom + 12f, 5.5f); right(c, "00:00", r.right, r.bottom + 12f, 5.5f)
            line(c, 453f, 123f, 453f, 598f)
            ruleTitle(c, s(R.string.lv_match_carbs), 459f, 116f, 125f, gray)
            text(c, recordedAverage("carbs"), 459f, 151f, 13f, true, width = 125f)
            ruleTitle(c, s(R.string.lv_match_insulin), 459f, 175f, 125f, gray)
            text(c, "${recordedAverage("insulin")} ${s(R.string.lv_report_units)}", 459f, 198f, 13f, true, width = 125f)
            wrap(c, s(R.string.lv_match_recorded_average), 459f, 216f, 125f, 6f, 4, gray)
            ruleTitle(c, s(R.string.lv_match_comments), 459f, 273f, 125f, gray)
            wrap(c, "${s(R.string.lv_match_missing_insulin)} ${dayCount - recordedDays("insulin")} / $dayCount", 459f, 295f, 125f, 7f, 6, gray)
            wrap(c, "${s(R.string.lv_match_missing_carbs)} ${dayCount - recordedDays("carbs")} / $dayCount", 459f, 352f, 125f, 7f, 6, gray)
        }
        private fun Int?.orEmptyCount() = this ?: 0

        private fun trends(c: Canvas) {
            val r = frame("trends.glucose")
            profile(c, r, interval = 2, timesTop = true, legend = false, timeLabelY = 119f)
            for (i in 0 until 12) {
                val values = points.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour / 2 == i }
                val x = r.left + i / 12f * r.width()
                outline(c, RectF(x, 127f, x + r.width() / 12f, 145f))
                text(c, if (values.isEmpty()) "-" else glucose(values.map { it.value }.average().toFloat()), x + 8f, 139f, 7f, true, width = 29f)
            }
            text(c, s(R.string.lv_report_mean), 90f, 120f, 6f, true, width = 70f)
            text(c, glucose(summary.avgMgDl), 131f, 139f, 8f, true, width = 30f)
            text(c, unit, 137f, 154f, 6f, width = 25f)
            val carbsPlot = frame("trends.carbs"); outline(c, carbsPlot)
            ruleTitle(c, s(R.string.lv_match_carbs), 66f, 337f, 90f, gray)
            val entries = amounts("carbs"); val maxAmount = entries.maxOfOrNull { it.amount!! }?.coerceAtLeast(1f) ?: 1f
            entries.forEach { e -> val x = clockX(e.timestamp, carbsPlot); fill(c, RectF(x - 1f, carbsPlot.bottom - e.amount!! / maxAmount * carbsPlot.height(), x + 1f, carbsPlot.bottom), orange) }
            for (h in 0..24 step 2) { val x = carbsPlot.left + h / 24f * carbsPlot.width(); line(c, x, carbsPlot.top, x, carbsPlot.bottom, dashed = true); text(c, "%02d:00".format(locale, h % 24), x - 10f, carbsPlot.top - 6f, 5f, color = gray, width = 25f) }
            val insulin = frame("trends.insulin"); outline(c, insulin)
            line(c, insulin.left, insulin.centerY(), insulin.right, insulin.centerY())
            wrap(c, s(R.string.lv_match_insulin), 66f, 474f, 80f, 7f, 3)
            text(c, recordedAverage("insulin"), 132f, 480f, 8f, true, width = 30f)
            val doses = amounts("insulin"); val recorded = recordedDays("insulin").coerceAtLeast(1)
            for (i in 0 until 12) {
                val values = doses.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour / 2 == i }
                val x = insulin.left + i / 12f * insulin.width()
                line(c, x, insulin.top, x, insulin.bottom)
                if (values.isNotEmpty()) text(c, "${number((values.sumOf { it.amount!!.toDouble() } / recorded).toFloat())} (${values.size})", x + 2f, insulin.top + 13f, 6f, width = insulin.width() / 12f - 3f)
                text(c, "%02d:00".format(locale, i * 2), x - 9f, insulin.top - 7f, 5f, color = gray, width = 25f)
            }
        }

        private fun journalTotal(day: LocalDate, type: String): String {
            val entries = journalByDay[day].orEmpty().filter { it.entryType == type && valid(it) }
            return if (entries.isEmpty()) "" else number(entries.sumOf { it.amount!!.toDouble() }.toFloat())
        }
        private fun weekly(c: Canvas, dates: List<LocalDate>) {
            val labels = listOf(R.string.lv_groups_mean_short, R.string.lv_match_carbs, R.string.lv_match_insulin, R.string.lv_groups_lows_short)
            labels.forEachIndexed { i, id -> wrap(c, s(id), 365f + i * 51f, 124f, 49f, 6f, 2, gray) }
            dates.forEachIndexed { i, day ->
                val y = 135f + i * 78f; val r = RectF(112f, y, 346f, y + 66f)
                text(c, day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), 66f, y + 10f, 9f, true, cyan, 40f)
                text(c, day.format(DateTimeFormatter.ofPattern("d MMM", locale)), 66f, y + 23f, 9f, true, cyan, 40f)
                trace(c, day, r)
                val entries = journalByDay[day].orEmpty().filter { it.entryType == "insulin" && valid(it) }
                var lastLabel = Float.NEGATIVE_INFINITY
                entries.forEach { entry ->
                    val x = clockX(entry.timestamp, r)
                    if (x - lastLabel >= 24f) { fill(c, RectF(x - 9f, r.top + 10f, x + 15f, r.top + 21f), Color.rgb(203, 233, 144)); text(c, number(entry.amount!!), x - 7f, r.top + 18f, 6.5f, true, width = 22f); lastLabel = x }
                }
                val values = listOf(dayDetails[day]?.let { glucose(it.averageMgDl) }.orEmpty(), journalTotal(day, "carbs"),
                    journalTotal(day, "insulin"), if (dayDetails[day] == null) "" else (lows[day] ?: 0).toString())
                outline(c, RectF(362f, y, 575f, y + 66f))
                values.forEachIndexed { col, value ->
                    if (col > 0) line(c, 362f + col * 53f, y, 362f + col * 53f, y + 66f, dashed = true)
                    text(c, value, 374f + col * 51f, y + 39f, 12f, true, width = 44f)
                }
            }
        }

        private fun monthly(c: Canvas, month: YearMonth) {
            text(c, month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), 66f, 83f, 8f, color = gray)
            val r = frame("monthly.calendar"); val width = r.width() / 7f
            val offset = month.atDay(1).dayOfWeek.value - 1
            val rows = (offset + month.lengthOfMonth() + 6) / 7
            val height = r.height() / rows
            (1..7).forEach { d -> text(c, java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, locale), r.left + (d - 1) * width + 5f, r.top - 7f, 6f, color = gray, width = width - 4f) }
            for (cell in 0 until rows * 7) {
                val dayNumber = cell - offset + 1; val x = r.left + cell % 7 * width; val y = r.top + cell / 7 * height
                outline(c, RectF(x, y, x + width, y + height))
                if (dayNumber !in 1..month.lengthOfMonth()) continue
                val day = month.atDay(dayNumber); val values = calendarDetails[day]
                fill(c, RectF(x + width - 13f, y, x + width, y + 10f), Color.rgb(225, 226, 228))
                right(c, dayNumber.toString(), x + width - 2f, y + 8f, 6f, width = 13f)
                if (values != null) {
                    text(c, "${glucose(values.averageMgDl)}", x + 6f, y + 23f, 8f, color = blue, width = width - 8f)
                    text(c, unit, x + 29f, y + 23f, 4.5f, color = gray, width = 26f)
                    text(c, "-", x + 6f, y + 35f, 7f, color = cyan, width = width - 8f)
                    val count = calendarLows[day] ?: 0
                    if (count > 0) text(c, "${s(R.string.lv_groups_lows_short)} $count", x + 6f, y + 46f, 6f, color = red, width = width - 8f)
                }
            }
            wrap(c, "${s(R.string.lv_report_mean)} ($unit)", 66f, 147f, 100f, 7f, 3, blue)
            wrap(c, s(R.string.lv_match_no_scan_log), 66f, 186f, 100f, 6f, 4, gray)
        }

        private fun dailyLog(c: Canvas, dates: List<LocalDate>) {
            dates.forEachIndexed { row, day ->
                val offset = row * 181f; val r = RectF(143f, 149f + offset, 574f, 229f + offset)
                text(c, day.format(DateTimeFormatter.ofPattern("EEE d LLLL", locale)).uppercase(locale), 44f, 135f + offset, 7f, width = 400f)
                trace(c, day, r, modern = true, timesTop = true)
                text(c, "${s(R.string.lv_report_glucose)} $unit", 44f, 190f + offset, 6f, width = 92f)
                val readings = daily[day].orEmpty().groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
                val entries = journalByDay[day].orEmpty().filter { valid(it) }.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
                val labels = listOf("${s(R.string.lv_groups_max)} $unit", "${s(R.string.lv_groups_min)} $unit", s(R.string.lv_match_insulin), s(R.string.lv_match_carbs))
                val hasCarbs = journalByDay[day].orEmpty().any { it.entryType == "carbs" && valid(it) }
                val nRows = if (hasCarbs) 4 else 3
                for (i in 0 until nRows) { right(c, labels[i], 137f, 242f + offset + i * 16f, 6f, width = 97f); line(c, 38f, 230f + offset + i * 16f, 574f, 230f + offset + i * 16f) }
                for (hour in 0 until 24) {
                    val values = readings[hour].orEmpty(); val amounts = entries[hour].orEmpty()
                    fun sum(type: String): String { val matched = amounts.filter { it.entryType == type }; return if (matched.isEmpty()) "" else number(matched.sumOf { it.amount!!.toDouble() }.toFloat()) }
                    val cells = listOf(values.maxOfOrNull { it.value }?.let(::glucose).orEmpty(), values.minOfOrNull { it.value }?.let(::glucose).orEmpty(), sum("insulin"), sum("carbs"))
                    val x = r.left + hour / 24f * r.width(); val w = r.width() / 24f
                    line(c, x, 230f + offset, x, 230f + offset + nRows * 16f)
                    for (i in 0 until nRows) {
                        if (i == 2 && cells[i].isNotBlank()) fill(c, RectF(x, 230f + offset + i * 16f, x + w, 246f + offset + i * 16f), Color.rgb(251, 231, 244))
                        text(c, cells[i], x + 1f, 242f + offset + i * 16f, 5.5f, i == 2, if (i == 2) Color.rgb(166, 38, 126) else ink, w - 1f)
                    }
                }
                line(c, 38f, 230f + offset + nRows * 16f, 574f, 230f + offset + nRows * 16f)
            }
        }

        private fun mealPart(time: Long) = when (Instant.ofEpochMilli(time).atZone(zone).hour) { in 4..9 -> 0; in 10..15 -> 1; in 16..21 -> 2; else -> 3 }
        private fun mealPage(c: Canvas, dates: List<LocalDate>) {
            val samples = meals.filter { date(it.timestamp) in dates }
            val names = listOf(R.string.lv_groups_morning, R.string.lv_groups_midday, R.string.lv_groups_evening, R.string.lv_groups_night)
            val panels = frame("meals.panels"); val tables = frame("meals.table")
            for (part in 0..3) {
                val x = panels.left + part * (panels.width() / 4f + 4f)
                val r = RectF(x, panels.top, x + 75f, panels.bottom)
                text(c, s(names[part]), x, 108f, 9f, true, cyan, 91f)
                text(c, listOf("04:00 - 10:00", "10:00 - 16:00", "16:00 - 22:00", "22:00 - 04:00")[part], x, 115f, 5f, color = gray, width = 91f)
                fill(c, RectF(r.left, valueY(targets.highMgDl, r), r.right, valueY(targets.lowMgDl, r)), Color.rgb(220, 221, 224))
                listOf(0f, 150f, 250f, 350f).forEach { tick -> right(c, glucose(tick), x - 4f, valueY(tick, r) + 2f, 5f, width = 20f) }
                outline(c, r)
                val matches = samples.filter { mealPart(it.timestamp) == part }
                val path = Path(); var connected = false
                for (step in 0..3) {
                    val values = matches.mapNotNull { it.glucose[step] }; val px = r.left + (step + 1) / 4f * r.width()
                    line(c, px, r.top, px, r.bottom, dashed = true)
                    text(c, if (step == 0) "0" else "+${step}h", px - 5f, r.bottom + 10f, 5f, width = 20f)
                    if (values.isEmpty()) connected = false else {
                        val py = valueY(values.average().toFloat(), r)
                        if (connected) path.lineTo(px, py) else path.moveTo(px, py)
                        paint.style = Paint.Style.FILL; paint.color = orange; c.drawCircle(px, py, 1f, paint)
                        connected = true
                    }
                }
                paint.style = Paint.Style.STROKE; paint.color = orange; paint.strokeWidth = 0.8f; c.drawPath(path, paint)
                val height = 13f; val top = tables.top; val columnWidth = r.width() / 4f
                fill(c, RectF(x + 3 * columnWidth, top, x + 4 * columnWidth, top + (dates.size + 1) * height), Color.rgb(255, 239, 218))
                for (row in 0..dates.size + 1) line(c, x, top + row * height, x + r.width(), top + row * height)
                for (col in 0..4) line(c, x + col * columnWidth, top, x + col * columnWidth, top + (dates.size + 1) * height)
                dates.forEachIndexed { row, day ->
                    val dailySamples = matches.filter { date(it.timestamp) == day }
                    for (step in 0..1) { val values = dailySamples.mapNotNull { it.glucose[step] }; if (values.isNotEmpty()) text(c, glucose(values.average().toFloat()), x + step * columnWidth + 1f, top + (row + 2) * height - 3f, 5f, width = columnWidth - 1f) }
                }
            }
            text(c, s(R.string.lv_report_mean), 66f, tables.top + 9f, 6f, true, width = 66f)
            dates.forEachIndexed { i, day -> text(c, day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), 66f, tables.top + (i + 2) * 13f - 3f, 6f, width = 68f) }
            wrap(c, s(R.string.lv_groups_meal_missing), 66f, tables.top + (dates.size + 2) * 13f + 22f, 500f, 6f, 2, gray)
        }

        private fun device(c: Canvas) {
            val settings = frame("device.settings"); val phone = frame("device.phone")
            val personal = state.reportPersonalTargets ?: targets
            ruleTitle(c, s(R.string.lv_match_settings), settings.left, settings.top, settings.width())
            text(c, s(R.string.lv_report_target), settings.left, 159f, 9f, true, width = 180f)
            right(c, "${glucose(personal.lowMgDl)} - ${glucose(personal.highMgDl)} $unit", settings.right, 159f, 9f, width = 140f)
            line(c, settings.left, 170f, settings.right, 170f)
            text(c, s(R.string.lv_match_clinical_thresholds), settings.left, 194f, 9f, true, width = 180f)
            text(c, "${glucose(personal.veryLowMgDl)} / ${glucose(personal.veryHighMgDl)} $unit", settings.left, 214f, 9f)
            fill(c, phone, pale)
            text(c, s(R.string.lv_groups_phone), phone.left + 10f, 181f, 8f, color = gray)
            text(c, "${Build.MANUFACTURER} ${Build.MODEL}", phone.left + 10f, 198f, 10f, width = phone.width() - 20f)
            text(c, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", phone.left + 10f, 228f, 9f, width = phone.width() - 20f)
            text(c, context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(), phone.left + 10f, 259f, 9f, width = phone.width() - 20f)
            ruleTitle(c, s(R.string.lv_groups_data_sources), 38f, 350f, 536f)
            points.mapNotNull { it.sensorSerial?.takeIf(String::isNotBlank) }.distinct().take(16).forEachIndexed { i, serial -> text(c, serial, 38f, 378f + i * 17f, 9f, width = 536f) }
        }
    }
}
