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
import java.text.Normalizer
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
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            hinting = Paint.HINTING_OFF
        }
        private fun font(id: Int, bold: Boolean = false): Typeface =
            runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
                ?: Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        // Static, fully bundled Latin Extended fonts also used by legacy report pages.
        // Keep one family throughout the PDF; avoid OEM fallback for Polish accents.
        private val reportRegular = font(R.font.report_arial_compatible_regular)
        private val reportBold = font(R.font.report_arial_compatible_bold, true)
        private var regularFont = reportRegular
        private var boldFont = reportBold
        private val ink = Color.rgb(20, 23, 29)
        private val gray = Color.rgb(85, 89, 96)
        private val border = Color.rgb(167, 170, 177)
        private val cyan = Color.rgb(70, 144, 197)
        private val blue = Color.rgb(0, 103, 172)
        private val green = Color.rgb(88, 145, 77)
        private val orange = Color.rgb(232, 166, 0)
        private val red = Color.rgb(211, 4, 24)
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
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            val shown = if (paint.measureText(normalized) <= width) normalized else {
                val count = paint.breakText(normalized, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
                normalized.take(count) + "…"
            }
            c.drawText(shown, x, y, paint)
        }
        private fun right(c: Canvas, value: String, x: Float, y: Float, size: Float = 8f,
                          bold: Boolean = false, color: Int = ink, width: Float = 100f) {
            paint.typeface = if (bold) boldFont else regularFont; paint.textSize = size
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
            text(c, normalized, x - minOf(width, paint.measureText(normalized)), y, size, bold, color, width)
        }
        private fun wrap(c: Canvas, value: String, x: Float, top: Float, width: Float,
                         size: Float = 7f, maxLines: Int = 3, color: Int = ink): Float {
            var rest = Normalizer.normalize(value, Normalizer.Form.NFC).trim(); var y = top
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
        private fun fill(c: Canvas, r: RectF, color: Int, radius: Float = 0f) {
            paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = color
            c.drawRoundRect(r, radius, radius, paint)
        }
        private fun clipRounded(c: Canvas, r: RectF, radius: Float) {
            val path = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
            c.clipPath(path)
        }
        private fun clinicalColor(value: Float): Int = when {
            value > targets.highMgDl -> orange
            value < targets.lowMgDl -> red
            else -> green
        }
        private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int = border,
                         width: Float = 0.4f, dashed: Boolean = false) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
            paint.strokeCap = Paint.Cap.BUTT; paint.strokeJoin = Paint.Join.ROUND
            paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(2f, 2f), 0f) else null
            c.drawLine(x1, y1, x2, y2, paint); paint.pathEffect = null
        }
        private fun outline(c: Canvas, r: RectF, radius: Float = 0f, color: Int = border) {
            paint.style = Paint.Style.STROKE; paint.pathEffect = null; paint.strokeWidth = 0.5f; paint.color = color
            c.drawRoundRect(r, radius, radius, paint)
        }
        private fun card(c: Canvas, key: String, title: String) {
            val r = frame(key); outline(c, r, 6f)
            text(c, title, r.left + 9f, r.top + 16f, 8.7f, true, width = r.width() - 18f)
        }
        private fun ruleTitle(c: Canvas, title: String, x: Float, y: Float, width: Float, color: Int = ink) {
            text(c, title, x, y, 9f, true, color, width); line(c, x, y + 5f, x + width, y + 5f, gray, 0.6f)
        }

        fun page(c: Canvas, spec: LibreViewReportPage, index: Int, total: Int, groupIndex: Int, groupTotal: Int) {
            c.drawColor(Color.WHITE)
            val modern = spec.group in setOf(LibreViewReportGroup.AGP, LibreViewReportGroup.GLUCOSE_INSIGHTS,
                LibreViewReportGroup.DAILY_LOG, LibreViewReportGroup.DEVICE)
            regularFont = reportRegular
            boldFont = reportBold
            if (modern) {
                text(c, patient?.name.orEmpty().ifBlank { "JugglucoNG" }, 38f, 48f, 10.9f, width = 370f)
                text(c, patient?.dateOfBirth.orEmpty().replace("-", "/").let { if (it.isBlank()) "" else "${s(R.string.lv_ref_dob).removeSuffix(":")}: $it" }, 38f, 62f, 8.7f, width = 370f)
                right(c, "JugglucoNG", 574f, 54f, 14f, true, width = 160f)
                text(c, s(spec.group.labelResId), 38f, 94f, 17.5f, true, width = 536f)
                if (spec.group != LibreViewReportGroup.DEVICE) {
                    fill(c, RectF(38f, 110f, if (spec.group == LibreViewReportGroup.DAILY_LOG) 574f else 302f, 128f), pale, 7f)
                    text(c, period, 44.5f, 122f, 8.7f, width = 520f)
                }
            } else {
                val weekly = spec.group == LibreViewReportGroup.WEEKLY_SUMMARY
                val headerX = if (weekly) 32f else 66f
                val headerY = if (weekly) 78f else 70f
                text(c, s(spec.group.labelResId), headerX, headerY, 15.6f, true, cyan, 410f)
                text(c, "${date(start).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))} - ${date(end).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))} ($dayCount ${s(R.string.lv_match_days)})", headerX, headerY + 12f, 8.9f, color = gray, width = 420f)
                right(c, "JugglucoNG", 584f, headerY, 18f, true, cyan, 140f)
                if (weekly) {
                    text(c, patient?.name.orEmpty(), 32f, 40f, 7.8f, width = 140f)
                    text(c, "${s(R.string.lv_ref_dob)} ${patient?.dateOfBirth.orEmpty().replace("-", "/")}", 32f, 52f, 7.8f, width = 140f)
                    text(c, "JugglucoNG", 178f, 40f, 7.8f, width = 250f)
                    right(c, "${s(R.string.lv_match_page)} ${groupIndex + 1}/$groupTotal", 584f, 40f, 7.8f, width = 100f)
                    right(c, "${s(R.string.lv_match_generated)} ${date(System.currentTimeMillis()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))}", 584f, 52f, 6f, width = 140f)
                } else {
                    val save = c.save(); c.rotate(-90f)
                    text(c, patient?.name.orEmpty(), -576f, 36f, 7.8f, width = 180f)
                    text(c, "${s(R.string.lv_ref_dob)} ${patient?.dateOfBirth.orEmpty().replace("-", "/")}", -576f, 48f, 7.8f, width = 180f)
                    text(c, "JugglucoNG", -438f, 36f, 7.8f, width = 180f)
                    text(c, "${s(R.string.lv_match_page)} ${groupIndex + 1}/$groupTotal", -96f, 36f, 7.8f, width = 100f)
                    text(c, "${s(R.string.lv_match_generated)} ${date(System.currentTimeMillis()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))}", -130f, 48f, 6f, width = 130f)
                    c.restoreToCount(save)
                }
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
            if (modern) {
                text(c, "${s(R.string.lv_match_generated)} ${date(System.currentTimeMillis()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale))}", 38f, 746f, 7.6f)
                right(c, "${s(R.string.lv_match_page)} ${groupIndex + 1}/$groupTotal", 574f, 746f, 7.6f, width = 130f)
                right(c, "JugglucoNG", 574f, 733f, 7.6f, true, width = 160f)
            }
        }

        private fun rangeCards(c: Canvas, insights: Boolean = false) {
            fill(c, RectF(310f, 110f, 574f, 128f), pale, 7f)
            text(c, "${s(R.string.lv_ref_coverage)} ${pct(summary.coverage.percent)}", 317f, 122f, 8.7f, width = 250f)
            card(c, "agp.ranges", s(R.string.lv_match_time_ranges))
            text(c, s(R.string.lv_ref_standard), 188f, 149f, 6.5f, color = gray, width = 107f)
            val colors = listOf(orange, Color.rgb(255, 220, 0), green, red, Color.rgb(80, 0, 16))
            val values = listOf(summary.tir.veryHighPercent, summary.tir.highPercent, summary.tir.inRangePercent, summary.tir.lowPercent, summary.tir.veryLowPercent)
            val labels = listOf(R.string.lv_report_very_high, R.string.lv_report_high, R.string.lv_report_target, R.string.lv_report_low, R.string.lv_report_very_low)
            // Reference geometry: 8.187 pt minimum category, proportional remainder,
            // and a 1.638 pt white gap. All five categories total 145.187 pt at 100%.
            // Outlined zero categories remain visible without painting a nonzero fill.
            var top = 164.086f
            val boundaries = mutableListOf<Float>()
            values.forEachIndexed { i, percent ->
                val height = 8.187f + percent.coerceIn(0f, 100f) * 0.977f
                val r = RectF(62.016f, top, 104.59f, top + height)
                fill(c, r, Color.WHITE, 2f)
                if (percent > 0f) fill(c, RectF(r.left, r.top + 0.6f, r.right, r.bottom - 0.6f), colors[i], 1.1f)
                outline(c, r, if (i == 0 || i == 4) 3f else 1.1f, colors[i])
                boundaries.add(r.bottom + 0.819f)
                top = r.bottom + 1.638f
            }
            // Upper and lower pairs have a two-column split; the central row spans it.
            val dividers = listOf(186.46f, 211.572f, 261.788f, 286.90f)
            boundaries.take(4).forEachIndexed { i, boundary ->
                val endX = if (i == 1 || i == 2) 302.18f else 194.38f
                val path = Path().apply {
                    moveTo(62.016f, boundary); lineTo(104.5f, boundary)
                    val direction = if (dividers[i] > boundary) 1f else -1f
                    quadTo(105.3f, boundary, 105.6f, boundary + direction)
                    lineTo(109.8f, dividers[i] - direction)
                    quadTo(110.1f, dividers[i], 111f, dividers[i])
                    lineTo(endX, dividers[i])
                }
                paint.style = Paint.Style.STROKE; paint.pathEffect = null
                paint.strokeWidth = 0.55f; paint.color = border
                c.drawPath(path, paint)
                val value = listOf(targets.veryHighMgDl, targets.highMgDl, targets.lowMgDl, targets.veryLowMgDl)[i]
                val badge = RectF(46.73f, boundary - 4.094f, 64.2f, boundary + 4.094f)
                fill(c, badge, Color.WHITE, 1f); outline(c, badge, 1f)
                right(c, glucose(value), 62f, boundary + 2.3f, 6.5f, width = 17f)
            }
            val ys = listOf(171f, 202f, 233f, 277f, 297f)
            values.forEachIndexed { i, value ->
                text(c, if (i == 2) s(R.string.lv_ref_range_short) else s(labels[i]), 114f, ys[i], if (i == 2) 8.7f else 7.6f, width = 83f)
                if (i != 2) right(c, pct(value), 193f, ys[i] + 2f, 9.8f, true, width = 45f)
            }
            text(c, "${glucose(targets.lowMgDl)} - ${glucose(targets.highMgDl)} ($unit)", 114f, 247f, 7.6f, width = 105f)
            line(c, 194.38f, 161.36f, 194.38f, 211.572f)
            line(c, 194.38f, 261.788f, 194.38f, 312f)
            right(c, pct(summary.tir.aboveRangePercent), 295f, 190f, 14f, true, width = 70f)
            right(c, pct(summary.tir.inRangePercent), 295f, 241f, 14f, true, width = 70f)
            right(c, pct(summary.tir.belowRangePercent), 295f, 291f, 14f, true, width = 70f)
            fun goal(value: String, x: Float, y: Float, width: Float = 51f, prefix: Boolean = true) {
                val label = if (prefix) "${s(R.string.lv_match_goal)} $value" else value
                paint.typeface = regularFont; paint.textSize = 7.6f
                val chipWidth = minOf(width, paint.measureText(label) + 4f)
                fill(c, RectF(x, y - 8f, x + chipWidth, y + 3f), Color.rgb(229, 230, 233), 2f)
                text(c, label, x + 2f, y, 7.6f, width = chipWidth - 4f)
            }
            goal("< 5%", 114f, 182f, 49f, prefix = false)
            goal("< 1%", 114f, 308f, 49f, prefix = false)
            goal("< 25%", 200f, 188f)
            goal("> 70%", 200f, 239f)
            goal("< 4%", 200f, 289f)
            outline(c, frame("agp.metrics"), 6f)
            text(c, s(R.string.lv_match_metrics), 318f, 146f, 8.7f, true, width = 245f)
            text(c, s(R.string.lv_ref_standard), 318f, 156f, 6.5f, width = 245f)
            line(c, 310f, 161f, 574f, 161f)
            line(c, 38f, 161f, 302f, 161f)
            val meanY = if (insights) 198f else 185f
            text(c, s(R.string.lv_ref_mean), 318f, meanY - 5f, 8.7f, width = 155f)
            right(c, unit, 566f, meanY + 4f, 8.7f, true, width = 40f)
            paint.typeface = boldFont; paint.textSize = 8.7f
            right(c, glucose(summary.avgMgDl), 563f - paint.measureText(unit), meanY + 4f, 13.1f, true, width = 70f)
            goal("< ${glucose(154f)} $unit", 318f, meanY + 9f, 100f)
            line(c, 310f, if (insights) 237f else 207f, 574f, if (insights) 237f else 207f)
            val gmiY = if (insights) 289f else 241f
            text(c, s(R.string.lv_match_gmi), 318f, gmiY - 18f, 8.7f, width = 245f)
            text(c, number(summary.gmiPercent), 318f, gmiY, 13.1f, true, width = 28f)
            text(c, "%", 340f, gmiY, 8.7f, true, width = 11f)
            text(c, ClinicalReportPolicy.gmiMmolMol(summary.avgMgDl).roundToInt().toString(), 406f, gmiY, 13.1f, true, width = 24f)
            text(c, "mmol/mol", 425f, gmiY, 8.7f, true, width = 47f)
            line(c, 399f, gmiY - 9f, 399f, gmiY + 3f)
            goal("< 7%", 353f, gmiY, 44f)
            goal("< 53 mmol/mol", 475f, gmiY, 88f)
            if (!insights) {
                line(c, 310f, 254f, 574f, 254f)
                text(c, s(R.string.lv_ref_variability), 318f, 269f, 8.7f, width = 180f)
                wrap(c, s(R.string.lv_ref_cv), 318f, 281f, 200f, 7.6f, 2, gray)
                right(c, "${number(summary.cvPercent)}%", 566f, 289f, 14f, true, width = 95f)
                goal("< 36%", 318f, 303f, 63f)
            }
        }

        private fun overview(c: Canvas) {
            rangeCards(c)
            card(c, "agp.profile", s(R.string.lv_report_profile))
            wrap(c, s(R.string.lv_ref_agp), 47f, 345f, 518f, 8.7f, 2)
            profile(c, frame("agp.plot"), modern = true)
            val rows = (recent.size + 6) / 7
            val dailyCard = frame("agp.daily").apply { bottom += (rows - 1) * 48f }
            outline(c, dailyCard, 6f)
            text(c, s(R.string.lv_report_daily_profiles), 47f, 549f, 8.7f, true)
            text(c, s(R.string.lv_ref_daily), 47f, 561f, 8.7f, width = 518f)
            recent.chunked(7).forEachIndexed { row, days ->
                val outer = RectF(74f, 587f + row * 48f, 74f + days.size * (488f / 7f), 626f + row * 48f)
                val clip = c.save(); clipRounded(c, outer, 6f)
                days.forEachIndexed { col, day ->
                    val x = outer.left + col * (488f / 7f)
                    trace(c, day, RectF(x, outer.top, x + 488f / 7f, outer.bottom), modern = true, compact = true, tile = false)
                    if (col > 0) line(c, x, outer.top, x, outer.bottom)
                    line(c, x + 244f / 7f, outer.top, x + 244f / 7f, outer.bottom, dashed = true)
                }
                c.restoreToCount(clip); outline(c, outer, 6f)
            }
            recent.forEachIndexed { i, day ->
                val x = 74f + (i % 7) * (488f / 7f); val y = 587f + (i / 7) * 48f
                val name = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
                text(c, name, x + 15f, y - 6f, 7.6f, width = 67f)
                text(c, day.dayOfMonth.toString(), x + 3f, y + 10f, 7.6f)
                text(c, "12:00", x + 23f, y + 48f, 7.6f, width = 30f)
                if (i % 7 == 0) listOf(targets.lowMgDl, targets.highMgDl).forEach {
                    right(c, glucose(it), x - 7f, valueY(it, RectF(x, y, x + 488f / 7f, y + 39f)) + 2f, 6.5f, width = 25f)
                }
            }
        }

        private fun insights(c: Canvas) {
            rangeCards(c, true)
            card(c, "insights.notes", s(R.string.lv_groups_observations))
            val items = summary.insights.take(3)
            if (items.isEmpty()) text(c, s(R.string.lv_groups_no_observations), 51f, 359f, 8.7f, width = 510f)
            items.firstOrNull()?.let { item ->
                fill(c, RectF(51f, 349f, 561f, 372f), pale, 5f)
                text(c, item.title, 58f, 364f, 8.7f, true, width = 495f)
                wrap(c, item.message, 57f, 390f, 242f, 7.6f, 6)
            }
            items.drop(1).forEachIndexed { i, item ->
                text(c, item.title, 315f, 387f + i * 36f, 8.7f, true, width = 244f)
                wrap(c, item.message, 315f, 400f + i * 36f, 244f, 7.6f, 2)
            }
            card(c, "insights.profile", s(R.string.lv_ref_insights_profile))
            val plot = frame("insights.plot")
            val parts = listOf(0f, 8f, 12f, 18f, 24f)
            fill(c, RectF(plot.left, plot.bottom, plot.right, plot.bottom + 34f), pale, 6f)
            profile(c, plot, modern = true, dottedOuter = true)
            listOf(R.string.lv_ref_night, R.string.lv_groups_morning, R.string.lv_groups_midday, R.string.lv_groups_evening)
                .forEachIndexed { i, title ->
                    val left = plot.left + parts[i] / 24f * plot.width()
                    val right = plot.left + parts[i + 1] / 24f * plot.width()
                    if (i > 0) line(c, left, plot.bottom, left, plot.bottom + 34f)
                    text(c, s(title), left + 8f, plot.bottom + 28f, 7.6f, color = gray, width = right - left - 12f)
                }
        }

        private fun valueY(value: Float, r: RectF, max: Float = 350f) = r.bottom - value.coerceIn(0f, max) / max * r.height()
        private fun clockX(timestamp: Long, r: RectF): Float {
            val t = Instant.ofEpochMilli(timestamp).atZone(zone)
            return r.left + (t.hour * 60f + t.minute + t.second / 60f) / 1440f * r.width()
        }
        private fun axes(c: Canvas, r: RectF, max: Float = 350f, interval: Int = 3,
                         modern: Boolean = false, compact: Boolean = false, timesTop: Boolean = false,
                         timeLabelY: Float? = null, dense: Boolean = false, labelSize: Float? = null) {
            val ticks = if (dense) (0..350 step 25).map { it.toFloat() } + listOf(targets.lowMgDl, targets.highMgDl)
                else if (compact) listOf(0f, targets.lowMgDl, targets.highMgDl, max)
                else listOf(0f, targets.veryLowMgDl, targets.lowMgDl, targets.highMgDl, targets.veryHighMgDl, max)
            val printed = mutableListOf<Float>()
            ticks.distinct().sortedBy { if (it == targets.lowMgDl || it == targets.highMgDl) 0 else 1 }.forEach { tick ->
                val y = valueY(tick, r, max); val target = tick == targets.lowMgDl || tick == targets.highMgDl
                if (!dense || target) line(c, if (target && modern) r.left - 47f else r.left, y, r.right, y, if (target && modern) green else border, if (target) 0.9f else 0.35f, !target)
                if (printed.none { abs(it - y) < if (dense) 4f else if (modern) 5.5f else 8f }) {
                    if (target && modern) {
                        fill(c, RectF(r.left - 68f, y - 5f, r.left - 47f, y + 5f), green, 2f)
                        text(c, glucose(tick), r.left - 66f, y + 3f, 8.7f, true, Color.WHITE, 21f)
                    } else right(c, glucose(tick), r.left - if (dense && target) 21f else 6f, y + 2f, labelSize ?: if (dense || compact) 5.6f else if (modern) 8.7f else 6.5f, color = gray, width = 35f)
                    printed.add(y)
                }
            }
            for (hour in 0..24 step interval) {
                val x = r.left + hour / 24f * r.width()
                line(c, x, r.top, x, r.bottom, border, 0.35f, true)
                val timeSize = labelSize ?: if (modern && !compact) 8.7f else 5.6f
                val timeX = if (timesTop && compact && hour == 0) x else if (timesTop && compact && hour == 24) x - 24f else x - if (modern || compact) 12f else 8f
                text(c, "%02d:00".format(locale, hour % 24), timeX,
                    timeLabelY ?: if (timesTop) r.top - 5f else r.bottom + 15f, timeSize, color = gray, width = 31f)
            }
            if (dense) {
                val high = valueY(targets.highMgDl, r); val low = valueY(targets.lowMgDl, r)
                val bracket = Path().apply {
                    moveTo(r.left - 26f, high); lineTo(r.left - 49f, high)
                    quadTo(r.left - 58f, high, r.left - 58f, high + 9f)
                    lineTo(r.left - 58f, low - 9f)
                    quadTo(r.left - 58f, low, r.left - 49f, low)
                    lineTo(r.left - 26f, low)
                }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.8f; paint.pathEffect = null; paint.color = gray
                c.drawPath(bracket, paint)
                wrap(c, s(R.string.lv_ref_range), r.left - 100f, (high + low) / 2f - 3f, 54f, 6.7f, 2, gray)
            }
            if (modern) {
                for (hour in 0..24) {
                    val x = r.left + hour / 24f * r.width()
                    line(c, x, r.bottom, x, r.bottom + 4f)
                }
                val middle = (valueY(targets.lowMgDl, r) + valueY(targets.highMgDl, r)) / 2f
                val words = s(R.string.lv_ref_range).split(' ', limit = 2)
                words.forEachIndexed { i, word -> right(c, word, r.left - 6f, middle - 1f + i * 9.9f, 8.7f, width = 65f) }
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
                            timesTop: Boolean = false, legend: Boolean = true, timeLabelY: Float? = null,
                            dense: Boolean = false, dottedOuter: Boolean = false) {
            fill(c, RectF(r.left, valueY(targets.highMgDl, r), r.right, valueY(targets.lowMgDl, r)), Color.rgb(239, 240, 241))
            val saved = c.save(); c.clipRect(r)
            fun x(bin: AgpReportBin) = r.left + (bin.index + 0.5f) / 96f * r.width()
            fun band(segment: List<AgpReportBin>, lower: (AgpReportBin) -> Float, upper: (AgpReportBin) -> Float, color: Int, outer: Boolean = false) {
                val path = curvePath(segment.map { x(it) to valueY(upper(it), r) })
                curvePath(segment.asReversed().map { x(it) to valueY(lower(it), r) }, path, false); path.close()
                paint.style = Paint.Style.FILL; paint.pathEffect = null; paint.color = color
                if (dottedOuter && outer) {
                    val clip = c.save(); c.clipPath(path)
                    var py = r.top
                    while (py <= r.bottom) {
                        var px = r.left
                        while (px <= r.right) {
                            paint.color = clinicalColor((r.bottom - py) / r.height() * 350f)
                            c.drawCircle(px, py, 0.6f, paint); px += 4f
                        }
                        py += 4f
                    }
                    c.restoreToCount(clip)
                } else c.drawPath(path, paint)
            }
            val segments = AgpReportProfile.segments(bins)
            segments.forEach { segment ->
                band(segment, { it.p05 }, { it.p95 }, if (modern) Color.rgb(213, 236, 204) else Color.rgb(220, 227, 239), outer = true)
                band(segment, { it.p25 }, { it.p75 }, if (modern) Color.rgb(151, 199, 132) else Color.rgb(156, 178, 209))
                if (modern) {
                    val above = c.save(); c.clipRect(r.left, r.top, r.right, valueY(targets.highMgDl, r))
                    if (!dottedOuter) band(segment, { it.p05 }, { it.p95 }, Color.rgb(255, 242, 172))
                    band(segment, { it.p25 }, { it.p75 }, Color.rgb(255, 219, 61)); c.restoreToCount(above)
                    val below = c.save(); c.clipRect(r.left, valueY(targets.lowMgDl, r), r.right, r.bottom)
                    if (!dottedOuter) band(segment, { it.p05 }, { it.p95 }, Color.rgb(252, 218, 223))
                    band(segment, { it.p25 }, { it.p75 }, Color.rgb(244, 147, 163)); c.restoreToCount(below)
                }
            }
            c.restoreToCount(saved)
            axes(c, r, interval = interval, modern = modern, compact = compact, timesTop = timesTop, timeLabelY = timeLabelY, dense = dense)
            // Keep the median in front of the grid. Clip the same smoothed path at
            // each target, so colours meet exactly where the curve crosses it.
            val medianClip = c.save(); c.clipRect(r)
            segments.forEach { segment ->
                val path = curvePath(segment.map { x(it) to valueY(it.median, r) })
                paint.style = Paint.Style.STROKE; paint.pathEffect = null
                paint.strokeWidth = if (modern) 1.64f else 1.05f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                paint.color = if (modern) Color.rgb(68, 112, 59) else blue
                c.drawPath(path, paint)
                if (modern) {
                    val above = c.save(); c.clipRect(r.left, r.top, r.right, valueY(targets.highMgDl, r))
                    paint.color = Color.rgb(181, 148, 0); c.drawPath(path, paint); c.restoreToCount(above)
                    val below = c.save(); c.clipRect(r.left, valueY(targets.lowMgDl, r), r.right, r.bottom)
                    paint.color = red; c.drawPath(path, paint); c.restoreToCount(below)
                }
                if (segment.size == 1) {
                    val bin = segment.first()
                    paint.style = Paint.Style.FILL; paint.color = if (modern) clinicalColor(bin.median) else blue
                    c.drawCircle(x(bin), valueY(bin.median, r), 1f, paint)
                }
            }
            c.restoreToCount(medianClip)
            text(c, unit, if (compact) r.left - 20f else r.left - 29f,
                if (compact) r.top - 9f else r.top + 12f, if (modern) 7.6f else 6f, color = gray, width = 28f)
            if (legend) {
                val last = bins.lastOrNull()
                if (last != null) {
                    val values = listOf(last.p95, last.p75, last.median, last.p25, last.p05)
                    val ys = mutableListOf<Float>()
                    values.forEach { value -> ys.add(maxOf(valueY(value, r), (ys.lastOrNull() ?: (r.top - 8f)) + 8f)) }
                    val overflow = ((ys.lastOrNull() ?: r.bottom) - r.bottom).coerceAtLeast(0f)
                    values.forEachIndexed { i, value ->
                        val y = ys[i] - overflow
                        text(c, listOf("95%", "75%", "50%", "25%", "5%")[i], r.right + 12f, y + 2f, if (modern) 8.7f else 6.5f, i == 2, gray, 30f)
                        line(c, r.right, valueY(value, r), r.right + 6f, valueY(value, r), gray, 0.3f)
                        line(c, r.right + 6f, valueY(value, r), r.right + 6f, y, gray, 0.3f)
                        line(c, r.right + 6f, y, r.right + 10f, y, gray, 0.3f)
                    }
                }
            }
        }

        private fun trace(c: Canvas, day: LocalDate, r: RectF, modern: Boolean = false, compact: Boolean = false, timesTop: Boolean = false, tile: Boolean = true) {
            // Clip the content as well as the border of the rounded daily tiles.
            val tileClip = c.save()
            if (compact && tile) clipRounded(c, r, 4f)
            fill(c, RectF(r.left, valueY(targets.highMgDl, r), r.right, valueY(targets.lowMgDl, r)), Color.rgb(221, 223, 225))
            if (!compact) axes(c, r, interval = if (modern) 2 else 6, modern = false, compact = true, timesTop = timesTop, labelSize = if (modern) 7.6f else 5.6f)
            if (modern && !compact) for (hour in 1 until 24 step 2) {
                val x = r.left + hour / 24f * r.width()
                line(c, x, r.top, x, r.bottom, border, 0.3f, true)
            }
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
                    if (!modern || compact) {
                        excursion(r.top, valueY(targets.highMgDl, r), targets.highMgDl, Color.rgb(255, 224, 59))
                        excursion(valueY(targets.lowMgDl, r), r.bottom, targets.lowMgDl, Color.rgb(252, 189, 202))
                    } else {
                        excursion(r.top, valueY(targets.veryHighMgDl, r), targets.veryHighMgDl, Color.rgb(255, 224, 59))
                    }
                    line(c, previousX, valueY(old.value, r), x, y, if (modern) green else blue, if (modern && !compact) 1.15f else 0.7f)
                    val over = c.save(); c.clipRect(r.left, r.top, r.right, valueY(targets.highMgDl, r))
                    line(c, previousX, valueY(old.value, r), x, y, if (modern) orange else blue, if (modern && !compact) 1.15f else 0.8f); c.restoreToCount(over)
                    val under = c.save(); c.clipRect(r.left, valueY(targets.lowMgDl, r), r.right, r.bottom)
                    line(c, previousX, valueY(old.value, r), x, y, if (modern) red else blue, if (modern && !compact) 1.15f else 0.8f); c.restoreToCount(under)
                } else {
                    paint.style = Paint.Style.FILL; paint.color = if (modern) clinicalColor(point.value) else blue
                    c.drawCircle(x, y, 0.65f, paint)
                }
                previous = point; previousX = x
            }
            c.restoreToCount(save)
            c.restoreToCount(tileClip)
            if (compact && tile) {
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
                val mean = label == s(R.string.lv_report_mean)
                if (mean) wrap(c, s(R.string.lv_ref_mean).uppercase(locale), 66f, y - 7f, 90f, 7.6f, 2)
                else text(c, label, 66f, y, 7f, width = 106f)
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
            val lowY = lowPlot.bottom - (targets.lowMgDl - 40f) / 60f * lowPlot.height()
            line(c, lowPlot.left, lowY, lowPlot.right, lowY, red, 0.6f)
            for (hour in 0..24 step 6) {
                val x = lowPlot.left + hour / 24f * lowPlot.width()
                line(c, x, lowPlot.top, x, lowPlot.bottom, dashed = true)
                if (hour in 1..23) text(c, "%02d:00".format(locale, hour), x - 9f, lowPlot.bottom + 12f, 5.5f, color = gray, width = 25f)
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
            val coverageLine = Path()
            coverage.forEachIndexed { i, value ->
                val x = r.left + i / 23f * r.width(); val y = r.bottom - value / 100f * r.height()
                if (i == 0) coverageLine.moveTo(x, y) else coverageLine.lineTo(x, y)
            }
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 0.7f; paint.pathEffect = null; paint.color = cyan
            c.drawPath(coverageLine, paint)
            line(c, r.left, r.centerY(), r.right, r.centerY(), dashed = true)
            for (hour in 0..24 step 6) {
                val x = r.left + hour / 24f * r.width()
                line(c, x, r.top, x, r.bottom)
                if (hour in 1..23) text(c, "%02d:00".format(locale, hour), x - 9f, r.bottom + 12f, 5.5f, color = gray, width = 25f)
            }
            text(c, "50%", r.left - 20f, r.centerY() + 2f, 6f)
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
            profile(c, r, interval = 2, timesTop = true, legend = false, timeLabelY = 121f, dense = true)
            for (i in 0 until 12) {
                val values = points.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour / 2 == i }
                val x = r.left + i / 12f * r.width()
                outline(c, RectF(x, 127f, x + r.width() / 12f, 145f))
                text(c, if (values.isEmpty()) "-" else glucose(values.map { it.value }.average().toFloat()), x + 8f, 139f, 7f, true, width = 29f)
            }
            text(c, s(R.string.lv_ref_daily_average), 90f, 120f, 6.7f, true, width = 70f)
            text(c, glucose(summary.avgMgDl), 131f, 139f, 8f, true, width = 30f)
            text(c, unit, 137f, 154f, 6f, width = 25f)
            fun plotLabel(label: String, x: Float, y: Float) {
                paint.typeface = regularFont; paint.textSize = 5.6f
                val w = paint.measureText(label) + 5f
                fill(c, RectF(x, y - 7f, x + w, y + 3f), Color.WHITE)
                outline(c, RectF(x, y - 7f, x + w, y + 3f), color = Color.rgb(156, 178, 209))
                text(c, label, x + 2f, y, 5.6f, width = w - 4f)
            }
            plotLabel(s(R.string.lv_ref_percentiles_inner), r.left + 25f, r.bottom - 8f)
            plotLabel(s(R.string.lv_ref_percentiles_outer), r.right - 97f, r.bottom - 8f)
            text(c, s(R.string.lv_ref_median), r.right - 109f, r.top + 27f, 5.6f, color = blue, width = 65f)
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
        private data class DoseLabel(val entry: JournalEntryEntity, val x: Float,
                                     val left: Float, val width: Float, val lane: Int)

        /** Labels may move into another lane; their event anchors never move or round to an hour. */
        private fun doseLabels(day: LocalDate, r: RectF, units: Boolean = false): List<DoseLabel> {
            val laneEnds = mutableListOf<Float>()
            return journalByDay[day].orEmpty().filter { it.entryType == "insulin" && valid(it) }
                .sortedBy { it.timestamp }.map { entry ->
                    val label = doseText(entry, units)
                    paint.typeface = boldFont; paint.textSize = if (units) 6.5f else 7.6f
                    val width = (paint.measureText(label) + 4f).coerceIn(14f, r.width())
                    val x = clockX(entry.timestamp, r)
                    val left = (x + 0.5f).coerceIn(r.left, r.right - width)
                    var lane = laneEnds.indexOfFirst { left >= it + 2f }
                    if (lane < 0) { lane = laneEnds.size; laneEnds.add(r.left) }
                    laneEnds[lane] = left + width
                    DoseLabel(entry, x, left, width, lane)
                }
        }

        private fun doseText(entry: JournalEntryEntity, units: Boolean) =
            number(entry.amount!!) + if (units) " ${s(R.string.lv_report_units)}" else ""

        /** Use the plotted CGM segment only; do not invent a value across a recording gap. */
        private fun glucoseAtDose(day: LocalDate, timestamp: Long): Float? {
            val data = daily[day].orEmpty()
            val index = data.binarySearchBy(timestamp) { it.timestamp }
            if (index >= 0) return data[index].value
            val next = -index - 1
            if (next <= 0 || next >= data.size) return null
            val a = data[next - 1]; val b = data[next]
            val span = b.timestamp - a.timestamp
            if (span <= 0L || span > gapLimit) return null
            return a.value + (b.value - a.value) * ((timestamp - a.timestamp).toDouble() / span).toFloat()
        }

        private data class DoseCurveAnchor(val x: Float, val y: Float)

        /** Actual curve position, or the nearest real sample within 15 minutes of the dose. */
        private fun doseCurveAnchor(day: LocalDate, timestamp: Long, r: RectF): DoseCurveAnchor? {
            glucoseAtDose(day, timestamp)?.let { return DoseCurveAnchor(clockX(timestamp, r), valueY(it, r)) }
            val data = daily[day].orEmpty()
            val index = data.binarySearchBy(timestamp) { it.timestamp }
            val next = if (index >= 0) index else -index - 1
            val nearest = listOfNotNull(data.getOrNull(next - 1), data.getOrNull(next))
                .minByOrNull { abs(it.timestamp - timestamp) } ?: return null
            if (abs(nearest.timestamp - timestamp) > 15 * 60_000L) return null
            // End at the sample's own x as well as y, never invent glucose in a gap.
            return DoseCurveAnchor(clockX(nearest.timestamp, r), valueY(nearest.value, r))
        }

        private fun insulinPen(c: Canvas, x: Float, y: Float, color: Int, bounds: RectF? = null,
                               preferLeft: Boolean = false) {
            // Mirror at plot edges so the complete syringe remains visible.
            val sx = if (preferLeft || (bounds != null && x + 8f > bounds.right)) -1f else 1f
            val sy = if (bounds != null && y - 8f < bounds.top) -1f else 1f
            fun stroke(ax: Float, ay: Float, bx: Float, by: Float, width: Float) =
                line(c, x + sx * ax, y - sy * ay, x + sx * bx, y - sy * by, color, width)
            stroke(0f, 0f, 2f, 2f, 0.6f)
            stroke(2f, 2f, 5f, 5f, 1.8f)
            stroke(4f, 6f, 6f, 4f, 0.6f)
            stroke(5f, 5f, 6.5f, 6.5f, 0.6f)
            stroke(5.5f, 7.5f, 7.5f, 5.5f, 0.6f)
        }

        private fun weekly(c: Canvas, dates: List<LocalDate>) {
            val labels = listOf(R.string.lv_groups_mean_short, R.string.lv_match_carbs, R.string.lv_match_insulin, R.string.lv_groups_lows_short)
            labels.forEachIndexed { i, id -> wrap(c, s(id), 365f + i * 45.5f, 124f, 43f, 6f, 2, gray) }
            dates.forEachIndexed { i, day ->
                val y = 135f + i * 78f; val r = RectF(112f, y, 346f, y + 66f)
                text(c, day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), 66f, y + 10f, 9f, true, cyan, 40f)
                text(c, day.format(DateTimeFormatter.ofPattern("d MMM", locale)), 66f, y + 23f, 9f, true, cyan, 40f)
                trace(c, day, r, timesTop = true)
                val annotations = doseLabels(day, r, units = true)
                val laneCount = (annotations.maxOfOrNull { it.lane } ?: 0) + 1
                val laneStep = minOf(12f, (r.height() - 8f) / laneCount)
                val badgeHeight = minOf(10f, laneStep * 0.9f)
                val badgeSize = minOf(6.5f, badgeHeight * 0.75f)
                val markerClip = c.save(); c.clipRect(r)
                val anchors = annotations.map { doseCurveAnchor(day, it.entry.timestamp, r) }
                annotations.forEachIndexed { i, dose ->
                    val anchor = anchors[i]
                    val top = r.top + 4f + dose.lane * laneStep
                    if (anchor != null) {
                        // Origin is the exact injection time; endpoint is a real part of the trace.
                        line(c, dose.x, top + badgeHeight, anchor.x, anchor.y, green, 0.9f)
                    }
                }
                // Draw all labels after the stems so leaders cannot run through another label.
                annotations.forEach { dose ->
                    val top = r.top + 4f + dose.lane * laneStep
                    fill(c, RectF(dose.left, top, dose.left + dose.width, top + badgeHeight), Color.rgb(162, 209, 89), 2f)
                    text(c, doseText(dose.entry, true), dose.left + 2f, top + badgeHeight * 0.75f, badgeSize, true, width = dose.width - 4f)
                    line(c, dose.x, top + badgeHeight, dose.x + 0.5f, top + badgeHeight, green, 0.9f)
                }
                // Syringes go last, above stems and dose badges, in both report types.
                annotations.forEachIndexed { i, dose ->
                    val anchor = anchors[i]
                    if (anchor != null) insulinPen(c, anchor.x, anchor.y, green, r)
                    else insulinPen(c, dose.x, r.top + 11f, green, r, preferLeft = true)
                }
                c.restoreToCount(markerClip)
                val values = listOf(dayDetails[day]?.let { glucose(it.averageMgDl) }.orEmpty(), journalTotal(day, "carbs"),
                    journalTotal(day, "insulin"), if (dayDetails[day] == null) "" else (lows[day] ?: 0).toString())
                outline(c, RectF(362f, y, 544f, y + 66f))
                values.forEachIndexed { col, value ->
                    if (col > 0) line(c, 362f + col * 45.5f, y, 362f + col * 45.5f, y + 66f, dashed = true)
                    if (col == 2 && value.isNotBlank()) fill(c, RectF(369f + col * 45.5f, y + 34f, 401f + col * 45.5f, y + 49f), Color.rgb(162, 209, 89), 2f)
                    text(c, value, 374f + col * 45.5f, y + 43f, 12f, true, width = 40f)
                    if (col == 0 && value.isNotBlank()) {
                        outline(c, RectF(382f, y + 15f, 387f, y + 22f), 0.7f, blue)
                        text(c, unit, 377f, y + 56f, 5.6f, color = gray, width = 34f)
                    }
                    if (col == 2 && value.isNotBlank()) {
                        insulinPen(c, 474f, y + 22f, green)
                        text(c, s(R.string.lv_report_units), 465f, y + 56f, 5.6f, color = gray, width = 34f)
                    }
                    if (col == 3 && value.isNotBlank()) {
                        outline(c, RectF(517f, y + 15f, 524f, y + 22f), color = red)
                        line(c, 520.5f, y + 16f, 520.5f, y + 21f, red, 0.7f)
                        line(c, 518.5f, y + 19f, 520.5f, y + 21f, red, 0.7f)
                        line(c, 522.5f, y + 19f, 520.5f, y + 21f, red, 0.7f)
                    }
                }
            }
            reportLegend(c, 710f)
        }

        private fun monthly(c: Canvas, month: YearMonth) {
            fill(c, RectF(65f, 73f, 580f, 88f), Color.WHITE)
            text(c, month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), 66f, 82f, 8f, color = gray)
            val r = frame("monthly.calendar"); val width = r.width() / 7f
            val offset = month.atDay(1).dayOfWeek.value - 1
            val rows = (offset + month.lengthOfMonth() + 6) / 7
            val height = r.height() / rows
            (1..7).forEach { d -> text(c, java.time.DayOfWeek.of(d).getDisplayName(TextStyle.FULL, locale), r.left + (d - 1) * width + 5f, r.top - 7f, 6f, color = gray, width = width - 4f) }
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
            val shape = RectF(141.7f, 148.3f, 574f, 230.4f)
            val annotations = dates.associateWith { doseLabels(it, shape) }
            val insulinHeight = annotations.mapValues { (_, doses) ->
                maxOf(16f, ((doses.maxOfOrNull { it.lane } ?: 0) + 1) * 12f + 4f)
            }
            val hasCarbs = dates.associateWith { day -> journalByDay[day].orEmpty().any { it.entryType == "carbs" && valid(it) } }
            val extraHeight = dates.maxOfOrNull { (insulinHeight[it] ?: 16f) - 16f + if (hasCarbs[it] == true) 16f else 0f } ?: 0f
            val rowPitch = 156.3f + extraHeight
            dates.forEachIndexed { row, day ->
                val offset = row * rowPitch
                val r = RectF(shape.left, shape.top + offset, shape.right, shape.bottom + offset)
                val doses = annotations[day].orEmpty()
                text(c, day.format(DateTimeFormatter.ofPattern("EEE d LLLL", locale)).uppercase(locale), 44.5f, 144f + offset, 8.7f, width = 400f)
                trace(c, day, r, modern = true, timesTop = true)
                text(c, "${s(R.string.lv_ref_glucose_short)} $unit", 44.5f, 192f + offset, 7.6f, width = 92f)
                val color = Color.rgb(166, 38, 126)
                val markerClip = c.save(); c.clipRect(r)
                doses.forEach { dose ->
                    val anchor = doseCurveAnchor(day, dose.entry.timestamp, r)
                    if (anchor != null) insulinPen(c, anchor.x, anchor.y, color, r)
                    else insulinPen(c, dose.x, r.top + 11f, color, r)
                }
                c.restoreToCount(markerClip)
                val readings = daily[day].orEmpty().groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
                val entries = journalByDay[day].orEmpty().filter { it.entryType == "carbs" && valid(it) }
                    .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour }
                val insulinTop = 262f + offset
                val insulinBottom = insulinTop + (insulinHeight[day] ?: 16f)
                val bottom = insulinBottom + if (hasCarbs[day] == true) 16f else 0f
                val rowTops = listOf(230f + offset, 246f + offset, insulinTop) + if (hasCarbs[day] == true) listOf(insulinBottom) else emptyList()
                val labels = listOf("${s(R.string.lv_groups_max)} $unit", "${s(R.string.lv_groups_min)} $unit", s(R.string.lv_match_insulin), s(R.string.lv_match_carbs))
                rowTops.forEachIndexed { i, top ->
                    right(c, labels[i], 137f, top + 12f, 7.6f, width = 97f)
                    if (i == 2) insulinPen(c, 46f, top + 12f, color)
                    line(c, 38f, top, 574f, top)
                }
                for (hour in 0 until 24) {
                    val values = readings[hour].orEmpty()
                    val x = r.left + hour / 24f * r.width(); val w = r.width() / 24f
                    line(c, x, rowTops.first(), x, bottom)
                    for (i in 0..1) {
                        val sample = if (i == 0) values.maxOfOrNull { it.value } else values.minOfOrNull { it.value }
                        val top = rowTops[i]
                        val alert = sample != null && sample < targets.lowMgDl
                        if (alert) fill(c, RectF(x + 1f, top + 3f, x + w - 1f, top + 14f), red, 2f)
                        text(c, sample?.let(::glucose).orEmpty(), x + 1f, top + 12f, 6.5f, true, if (alert) Color.WHITE else ink, w - 1f)
                    }
                    if (hasCarbs[day] == true) {
                        val carbs = entries[hour].orEmpty()
                        if (carbs.isNotEmpty()) text(c, number(carbs.sumOf { it.amount!!.toDouble() }.toFloat()), x + 1f, insulinBottom + 12f, 6.5f, true, width = w - 1f)
                    }
                }
                // Each injection gets its own badge at its actual clock x, rather than an hourly sum.
                doses.forEach { dose ->
                    val top = insulinTop + 2f + dose.lane * 12f
                    line(c, dose.x, insulinTop, dose.x, top + 2f, color, 0.6f)
                    fill(c, RectF(dose.left, top, dose.left + dose.width, top + 11f), Color.rgb(251, 231, 244), 2f)
                    text(c, doseText(dose.entry, false), dose.left + 2f, top + 8.5f, 7.6f, true, color, dose.width - 4f)
                }
                line(c, 38f, bottom, 574f, bottom)
            }
            reportLegend(c, 694f, modern = true)
        }

        private fun reportLegend(c: Canvas, y: Float, modern: Boolean = false) {
            if (modern) fill(c, RectF(38f, y, 574f, y + 26f), pale, 5f)
            text(c, s(R.string.lv_ref_legend), 43f, y + 10f, 6.5f, true, width = 54f)
            fill(c, RectF(100f, y + 4f, 106f, y + 11f), orange, 1f)
            val high = if (modern) targets.veryHighMgDl else targets.highMgDl
            text(c, "${s(R.string.lv_ref_high)} (> ${glucose(high)})", 110f, y + 10f, 6.5f, color = gray, width = 160f)
            fill(c, RectF(276f, y + 4f, 282f, y + 11f), red, 1f)
            text(c, "${s(R.string.lv_ref_low)} (< ${glucose(targets.lowMgDl)})", 286f, y + 10f, 6.5f, color = gray, width = 145f)
            insulinPen(c, 439f, y + 11f, if (modern) Color.rgb(166, 38, 126) else green)
            text(c, s(R.string.lv_ref_recorded_insulin), 449f, y + 10f, 6.5f, color = gray, width = 120f)
            text(c, s(R.string.lv_ref_dose_anchor_note), 43f, y + 22f, 6f, color = gray, width = 526f)
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
                text(c, "-1h", r.left - 4f, r.bottom + 10f, 5f, color = gray, width = 20f)
                line(c, r.left + r.width() / 4f, r.top, r.left + r.width() / 4f, r.bottom, orange, 0.7f)
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
                // Four compact table keys: pre/post glucose, insulin and carbs.
                for (col in 0..3) {
                    val cx = x + (col + 0.5f) * columnWidth
                    if (col < 2) {
                        val drop = Path().apply {
                            moveTo(cx, top - 15f); cubicTo(cx - 5f, top - 9f, cx - 4f, top - 6f, cx, top - 6f)
                            cubicTo(cx + 4f, top - 6f, cx + 5f, top - 9f, cx, top - 15f); close()
                        }
                        paint.style = Paint.Style.FILL; paint.color = blue; c.drawPath(drop, paint)
                    } else if (col == 2) {
                        line(c, cx - 3f, top - 7f, cx + 3f, top - 13f, green, 2f)
                    } else fill(c, RectF(cx - 3f, top - 13f, cx + 3f, top - 7f), orange, 2f)
                }
                fill(c, RectF(x + 3 * columnWidth, top, x + 4 * columnWidth, top + (dates.size + 1) * height), Color.rgb(255, 239, 218))
                for (row in 0..dates.size + 1) line(c, x, top + row * height, x + r.width(), top + row * height)
                for (col in 0..4) line(c, x + col * columnWidth, top, x + col * columnWidth, top + (dates.size + 1) * height)
                dates.forEachIndexed { row, day ->
                    val dailySamples = matches.filter { date(it.timestamp) == day }
                    for (step in 0..1) { val values = dailySamples.mapNotNull { it.glucose[step] }; if (values.isNotEmpty()) text(c, glucose(values.average().toFloat()), x + step * columnWidth + 1f, top + (row + 2) * height - 3f, 5f, width = columnWidth - 1f) }
                }
            }
            text(c, s(R.string.lv_groups_mean_short).uppercase(locale), 70f, tables.top + 9f, 6.5f, true, width = 66f)
            dates.forEachIndexed { i, day -> text(c, day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)), 66f, tables.top + (i + 2) * 13f - 3f, 6f, width = 68f) }
            wrap(c, s(R.string.lv_groups_meal_missing), 66f, tables.top + (dates.size + 2) * 13f + 22f, 500f, 6f, 2, gray)
        }

        private fun device(c: Canvas) {
            val settings = frame("device.settings"); val phone = frame("device.phone")
            val personal = state.reportPersonalTargets ?: targets
            ruleTitle(c, s(R.string.lv_match_settings), settings.left, 122f, 536f)
            text(c, s(R.string.lv_report_target), settings.left, 159f, 9f, true, width = 180f)
            right(c, "${glucose(personal.lowMgDl)} - ${glucose(personal.highMgDl)} $unit", settings.right, 159f, 9f, width = 140f)
            line(c, settings.left, 170f, settings.right, 170f)
            text(c, s(R.string.lv_match_clinical_thresholds), settings.left, 194f, 9f, true, width = 180f)
            text(c, "${glucose(personal.veryLowMgDl)} / ${glucose(personal.veryHighMgDl)} $unit", settings.left, 214f, 9f)
            fill(c, phone, pale, 7f)
            // Neutral phone silhouette; model/software values come from this device.
            fill(c, RectF(phone.left + 18f, phone.top + 12f, phone.left + 31f, phone.top + 40f), Color.rgb(50, 53, 57), 2f)
            text(c, s(R.string.lv_ref_app), phone.left + 11f, phone.top + 56f, 8.7f, color = gray)
            text(c, "JugglucoNG", phone.left + 11f, phone.top + 70f, 8.7f)
            text(c, s(R.string.lv_ref_version), phone.left + 11f, phone.top + 89f, 8.7f, color = gray, width = phone.width() - 18f)
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            text(c, version, phone.left + 11f, phone.top + 103f, 8.7f, width = phone.width() - 18f)
            text(c, s(R.string.lv_ref_os), phone.left + 11f, phone.top + 122f, 8.7f, color = gray, width = phone.width() - 18f)
            text(c, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", phone.left + 11f, phone.top + 136f, 8.7f, width = phone.width() - 18f)
            text(c, s(R.string.lv_ref_phone_model), phone.left + 11f, phone.top + 155f, 8.7f, color = gray, width = phone.width() - 18f)
            text(c, "${Build.MANUFACTURER} ${Build.MODEL}", phone.left + 11f, phone.top + 169f, 8.7f, width = phone.width() - 18f)

            ruleTitle(c, s(R.string.lv_groups_data_sources), 38f, 350f, 536f)
            points.mapNotNull { it.sensorSerial?.takeIf(String::isNotBlank) }.distinct().take(16).forEachIndexed { i, serial -> text(c, serial, 38f, 378f + i * 17f, 9f, width = 536f) }
        }
    }
}


