package tk.glucodata.ui.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.annotation.StringRes
import tk.glucodata.R
import tk.glucodata.data.HistoryDatabase
import tk.glucodata.data.journal.JournalEntryEntity
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.util.GlucoseFormatter
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A separate print layout. Other PDF styles and their calculations are untouched. */
internal object LibreViewStyleReport {
    suspend fun write(
        context: Context,
        uri: Uri,
        state: StatsUiState,
        reportDays: Int,
        patient: StatsReportExporter.PatientInfo?
    ) {
        require(state.readings.isNotEmpty()) { context.getString(R.string.lv_report_no_data) }
        val now = System.currentTimeMillis()
        val end = state.activeRange?.endMillis ?: state.summary.lastTimestamp.takeIf { it > 0L } ?: now
        val start = state.activeRange?.startMillis ?: (end - reportDays.coerceAtLeast(1) * 86_400_000L)
        val points = state.readings.filter {
            it.timestamp in start..end && it.value.isFinite() && it.value > 0f
        }.sortedBy { it.timestamp }
        require(points.isNotEmpty()) { context.getString(R.string.lv_report_no_data) }
        // A failed journal query must fail the export, rather than claim there were no doses.
        val journal = HistoryDatabase.getInstance(context).journalDao().getEntriesBetween(start, end)
        val renderer = Renderer(context, state, points, journal, start, end, patient?.sanitized())
        val document = PdfDocument()
        try {
            repeat(3) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                renderer.page(page.canvas, index)
                document.finishPage(page)
            }
            context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: throw IOException("Unable to open destination file")
        } finally {
            document.close()
        }
    }

    private class Renderer(
        val context: Context,
        val state: StatsUiState,
        val points: List<GlucosePoint>,
        val journal: List<JournalEntryEntity>,
        val start: Long,
        val end: Long,
        val patient: StatsReportExporter.PatientInfo?
    ) {
        private val zone = ZoneId.systemDefault()
        private val locale = Locale.getDefault()
        private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
        private val days = ((end - start) / 86_400_000.0).coerceAtLeast(1.0)
        private val summary = state.summary
        private val targets = state.targets
        private val bins = AgpReportProfile.build(points, zone)
        private val gapLimit = (StatsAnalytics.estimateCadenceMillis(points) * 3L).coerceAtMost(15 * 60_000L)
        private val daily = points.groupBy { date(it.timestamp) }
        private val lastDate = date(end)
        private val recentDates = (13 downTo 0).map { lastDate.minusDays(it.toLong()) }
            .filter { !it.isBefore(date(start)) }
        private val blue = Color.rgb(0, 103, 166)
        private val green = Color.rgb(57, 164, 75)
        private val gray = Color.rgb(115, 115, 115)
        private val light = Color.rgb(224, 229, 237)
        private val middle = Color.rgb(158, 181, 210)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val unit = if (state.unit == GlucoseUnit.MMOL) "mmol/L" else "mg/dL"
        private val rangeColors = listOf(
            Color.rgb(239, 171, 0), Color.rgb(245, 220, 0), green,
            Color.rgb(238, 90, 90), Color.rgb(186, 20, 47)
        )
        private val rangePercents = listOf(
            summary.tir.veryHighPercent, summary.tir.highPercent, summary.tir.inRangePercent,
            summary.tir.lowPercent, summary.tir.veryLowPercent
        )
        private val rangeNames = listOf(
            R.string.lv_report_very_high, R.string.lv_report_high, R.string.lv_report_target,
            R.string.lv_report_low, R.string.lv_report_very_low
        )
        private val rangeBounds get() = listOf(
            ">= ${glucose(targets.veryHighMgDl)} $unit",
            "> ${glucose(targets.highMgDl)} - < ${glucose(targets.veryHighMgDl)} $unit",
            "${glucose(targets.lowMgDl)} - ${glucose(targets.highMgDl)} $unit",
            "${glucose(targets.veryLowMgDl)} - < ${glucose(targets.lowMgDl)} $unit",
            "< ${glucose(targets.veryLowMgDl)} $unit"
        )

        private fun s(@StringRes id: Int) = context.getString(id)
        private fun date(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        private fun glucose(value: Float) = GlucoseFormatter.formatFromMgDl(value, state.unit == GlucoseUnit.MMOL)
        private fun number(value: Float, digits: Int = 1) = String.format(locale, "%.${digits}f", value)
        private fun percent(value: Float) = "${number(value)}%"
        private fun duration(percent: Float): String {
            val minutes = (percent * 14.4f).roundToInt()
            return "${minutes / 60} h ${minutes % 60} min"
        }

        private fun text(c: Canvas, value: String, x: Float, y: Float, size: Float = 9f,
                         bold: Boolean = false, color: Int = Color.BLACK, width: Float = 535f) {
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize = size
            // All headers and patient fields have bounded space, including unusually long names.
            val fitted = if (paint.measureText(value) <= width) value else {
                val count = paint.breakText(value, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
                value.take(count) + "…"
            }
            c.drawText(fitted, x, y, paint)
        }

        private fun right(c: Canvas, value: String, x: Float, y: Float, size: Float = 9f,
                          bold: Boolean = false, color: Int = Color.BLACK, width: Float = 160f) {
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            val measured = paint.measureText(value)
            text(c, value, x - minOf(measured, width), y, size, bold, color, width)
        }

        private fun rect(c: Canvas, r: RectF, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = color
            c.drawRect(r, paint)
        }

        private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float,
                         color: Int = Color.LTGRAY, width: Float = 0.5f) {
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.strokeWidth = width
            c.drawLine(x1, y1, x2, y2, paint)
        }

        private fun section(c: Canvas, title: String, y: Float, x: Float = 30f, width: Float = 535f) {
            rect(c, RectF(x, y, x + width, y + 17f), Color.BLACK)
            text(c, title, x + 5f, y + 12f, 9f, true, Color.WHITE, width - 10f)
        }

        fun page(c: Canvas, index: Int) {
            c.drawColor(Color.WHITE)
            text(c, patient?.name?.takeIf { it.isNotBlank() } ?: "JugglucoNG", 30f, 27f, 10f, true, width = 330f)
            right(c, "${index + 1} / 3", 565f, 27f)
            val metadata = listOfNotNull(
                patient?.dateOfBirth?.takeIf { it.isNotBlank() }?.let { s(R.string.patient_dob_label) + ": " + it },
                patient?.identifier?.takeIf { it.isNotBlank() }?.let { "ID: $it" },
                patient?.clinician?.takeIf { it.isNotBlank() }
            ).joinToString("  |  ")
            text(c, metadata, 30f, 41f, 7f, color = gray, width = 365f)
            right(c, date(System.currentTimeMillis()).format(dateFormat), 565f, 41f, 8f, color = gray)
            val titles = listOf(R.string.lv_report_title, R.string.lv_report_snapshot, R.string.lv_report_daily_trends)
            text(c, s(titles[index]), 30f, 65f, 18f, true, blue)
            text(c, "${date(start).format(dateFormat)} - ${date(end).format(dateFormat)}", 30f, 82f, 10f, color = gray)
            when (index) {
                0 -> overview(c)
                1 -> snapshot(c)
                else -> trends(c)
            }
            text(c, s(R.string.lv_report_footer), 30f, 816f, 6.5f, color = gray)
            text(c, s(R.string.lv_report_method), 30f, 827f, 6.5f, color = gray)
        }

        private fun metric(c: Canvas, label: String, value: String, y: Float,
                           x: Float = 30f, width: Float = 285f) {
            text(c, label, x, y, 9f, width = width * 0.64f)
            right(c, value, x + width, y, 10f, true, width = width * 0.35f)
            line(c, x, y + 6f, x + width, y + 6f)
        }

        private fun overview(c: Canvas) {
            section(c, s(R.string.lv_report_statistics), 100f, width = 285f)
            section(c, s(R.string.lv_report_ranges), 100f, 330f, 235f)
            metric(c, s(R.string.lv_report_coverage), percent(summary.coverage.percent), 140f)
            metric(c, s(R.string.lv_report_readings), summary.readingCount.toString(), 168f)
            metric(c, s(R.string.lv_report_mean), "${glucose(summary.avgMgDl)} $unit", 196f)
            metric(c, "GMI", "${number(summary.gmiPercent)}%", 224f)
            metric(c, s(R.string.lv_report_variability), "${number(summary.cvPercent)}%", 252f)
            text(c, "${s(R.string.lv_report_target)}: ${glucose(targets.lowMgDl)} - ${glucose(targets.highMgDl)} $unit",
                30f, 282f, 9f, true)
            text(c, s(R.string.lv_report_thresholds), 30f, 299f, 7f, color = gray, width = 285f)
            // Stack geometry uses exact percentages; tiny/zero bands are not artificially enlarged.
            var barY = 130f
            rangePercents.forEachIndexed { i, p ->
                val height = p.coerceIn(0f, 100f) * 1.75f
                rect(c, RectF(335f, barY, 358f, barY + height), rangeColors[i])
                barY += height
                val y = 139f + i * 37f
                text(c, s(rangeNames[i]), 369f, y, 9f, true, width = 120f)
                right(c, percent(p), 565f, y, 10f, true, width = 65f)
                text(c, rangeBounds[i], 369f, y + 11f, 7f, width = 124f)
                right(c, duration(p), 565f, y + 11f, 7f, color = gray, width = 65f)
                line(c, 369f, y + 18f, 565f, y + 18f)
            }
            section(c, s(R.string.lv_report_profile), 335f)
            text(c, s(R.string.lv_report_percentiles), 30f, 365f, 8f, color = gray)
            profile(c, RectF(75f, 388f, 520f, 555f))
            section(c, s(R.string.lv_report_daily_profiles), 590f)
            text(c, s(R.string.lv_report_last_14), 30f, 620f, 8f, color = gray)
            val cellWidth = 535f / 7f
            recentDates.forEachIndexed { index, day ->
                val left = 30f + (index % 7) * cellWidth
                val top = 640f + (index / 7) * 82f
                miniDay(c, day, RectF(left + 2f, top, left + cellWidth - 2f, top + 54f))
            }
        }

        private fun y(value: Float, r: RectF, max: Float) = r.bottom - value.coerceIn(0f, max) / max * r.height()
        private fun maxGlucose() = maxOf(350f, targets.veryHighMgDl + 50f, bins.maxOfOrNull { it.p95 } ?: 350f)

        private fun axes(c: Canvas, r: RectF, max: Float, interval: Int = 3) {
            val ticks = listOf(0f, targets.veryLowMgDl, targets.lowMgDl, targets.highMgDl, targets.veryHighMgDl, max)
                .distinct().sorted()
            ticks.forEach { value ->
                val py = y(value, r, max)
                val isTarget = value == targets.lowMgDl || value == targets.highMgDl
                line(c, r.left, py, r.right, py, if (isTarget) green else Color.LTGRAY, if (isTarget) 0.9f else 0.3f)
            }
            val labelPositions = mutableListOf<Float>()
            ticks.sortedBy { if (it == targets.lowMgDl || it == targets.highMgDl) 0 else 1 }.forEach { value ->
                val py = y(value, r, max)
                val isTarget = value == targets.lowMgDl || value == targets.highMgDl
                if (isTarget || labelPositions.none { abs(it - py) < 10f }) {
                    right(c, glucose(value), r.left - 5f, py + 3f, 7f, isTarget, width = 40f)
                    labelPositions.add(py)
                }
            }
            for (hour in 0..24 step interval) {
                val px = r.left + hour / 24f * r.width()
                line(c, px, r.top, px, r.bottom, Color.LTGRAY, 0.3f)
                text(c, "%02d:00".format(locale, hour % 24), px - 11f, r.bottom + 13f, 7f, width = 30f)
            }
            text(c, unit, r.left, r.top - 9f, 7f, color = gray)
        }

        private fun profile(c: Canvas, r: RectF) {
            val max = maxGlucose()
            rect(c, RectF(r.left, y(targets.highMgDl, r, max), r.right, y(targets.lowMgDl, r, max)), Color.rgb(243, 248, 241))
            axes(c, r, max)
            val save = c.save()
            c.clipRect(r)
            fun x(bin: AgpReportBin) = r.left + (bin.index + 0.5f) / 96f * r.width()
            AgpReportProfile.segments(bins).forEach { segment ->
                fun band(lower: (AgpReportBin) -> Float, upper: (AgpReportBin) -> Float, color: Int) {
                    if (segment.size == 1) {
                        val bin = segment.first()
                        rect(c, RectF(x(bin) - r.width() / 192f, y(upper(bin), r, max),
                            x(bin) + r.width() / 192f, y(lower(bin), r, max)), color)
                        return
                    }
                    val path = Path()
                    segment.forEachIndexed { i, bin ->
                        if (i == 0) path.moveTo(x(bin), y(upper(bin), r, max)) else path.lineTo(x(bin), y(upper(bin), r, max))
                    }
                    segment.asReversed().forEach { bin -> path.lineTo(x(bin), y(lower(bin), r, max)) }
                    path.close()
                    paint.style = Paint.Style.FILL
                    paint.color = color
                    c.drawPath(path, paint)
                }
                band({ it.p05 }, { it.p95 }, light)
                band({ it.p25 }, { it.p75 }, middle)
                val path = Path()
                segment.forEachIndexed { i, bin ->
                    if (i == 0) path.moveTo(x(bin), y(bin.median, r, max)) else path.lineTo(x(bin), y(bin.median, r, max))
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f
                paint.color = blue
                c.drawPath(path, paint)
                if (segment.size == 1) {
                    paint.style = Paint.Style.FILL
                    c.drawCircle(x(segment.first()), y(segment.first().median, r, max), 1.4f, paint)
                }
            }
            c.restoreToCount(save)
            // Print target lines above the percentile bands, as in the reference report.
            listOf(targets.lowMgDl, targets.highMgDl).forEach { value ->
                line(c, r.left, y(value, r, max), r.right, y(value, r, max), green, 0.8f)
            }
            // Fixed legend spacing remains legible even when percentile values coincide.
            listOf("95%", "75%", "50%", "25%", "5%").forEachIndexed { i, label ->
                text(c, label, r.right + 6f, r.top + 18f + i * 13f, 8f, i == 2, if (i == 2) blue else gray, 35f)
            }
        }

        private fun miniDay(c: Canvas, day: LocalDate, r: RectF) {
            text(c, day.format(DateTimeFormatter.ofPattern("dd.MM EEE", locale)), r.left, r.top - 5f, 7f, width = r.width())
            val max = maxGlucose()
            rect(c, RectF(r.left, y(targets.highMgDl, r, max), r.right, y(targets.lowMgDl, r, max)), Color.rgb(237, 242, 235))
            listOf(targets.lowMgDl, targets.highMgDl).forEach {
                line(c, r.left, y(it, r, max), r.right, y(it, r, max), gray, 0.3f)
                if (recentDates.indexOf(day) % 7 == 0) {
                    right(c, glucose(it), r.left - 3f, y(it, r, max) + 2f, 6f, width = 25f)
                }
            }
            val values = daily[day].orEmpty()
            if (values.isEmpty()) text(c, s(R.string.lv_report_no_data_short), r.left + 2f, r.centerY(), 7f, color = gray, width = r.width() - 4f)
            else {
                val save = c.save()
                c.clipRect(r)
                val path = Path()
                var previous: GlucosePoint? = null
                var previousX = Float.NEGATIVE_INFINITY
                values.forEach { point ->
                    val local = Instant.ofEpochMilli(point.timestamp).atZone(zone)
                    val px = r.left + (local.hour * 60 + local.minute) / 1440f * r.width()
                    val py = y(point.value, r, max)
                    if (previous == null || point.timestamp - previous!!.timestamp > gapLimit || px < previousX) {
                        path.moveTo(px, py)
                        paint.style = Paint.Style.FILL
                        paint.color = blue
                        c.drawCircle(px, py, 0.8f, paint)
                    } else path.lineTo(px, py)
                    previous = point
                    previousX = px
                }
                paint.style = Paint.Style.STROKE
                paint.color = blue
                paint.strokeWidth = 0.8f
                c.drawPath(path, paint)
                c.restoreToCount(save)
            }
            line(c, r.left, r.bottom, r.right, r.bottom, gray)
            text(c, "00:00", r.left, r.bottom + 10f, 6f, color = gray, width = 25f)
            right(c, "24:00", r.right, r.bottom + 10f, 6f, color = gray, width = 25f)
        }

        private fun snapshot(c: Canvas) {
            section(c, s(R.string.lv_report_glucose), 100f)
            metric(c, s(R.string.lv_report_mean), "${glucose(summary.avgMgDl)} $unit", 145f, width = 245f)
            metric(c, s(R.string.lv_report_above), percent(summary.tir.aboveRangePercent), 183f, width = 245f)
            metric(c, s(R.string.lv_report_target), percent(summary.tir.inRangePercent), 221f, width = 245f)
            metric(c, s(R.string.lv_report_below), percent(summary.tir.belowRangePercent), 259f, width = 245f)
            profile(c, RectF(320f, 145f, 525f, 285f))
            section(c, s(R.string.lv_report_low_events), 325f)
            metric(c, s(R.string.lv_report_event_count), summary.lowEpisodes.count.toString(), 369f, width = 245f)
            metric(c, s(R.string.lv_report_median_duration), "${summary.lowEpisodes.medianDurationMinutes} min", 404f, width = 245f)
            val hours = IntArray(24)
            summary.episodes.filter { it.kind == EpisodeKind.LOW }.forEach {
                hours[Instant.ofEpochMilli(it.startMillis).atZone(zone).hour]++
            }
            val maxCount = hours.maxOrNull()?.coerceAtLeast(1) ?: 1
            for (hour in hours.indices) {
                val x = 310f + hour * 10f
                rect(c, RectF(x, 423f - hours[hour] / maxCount.toFloat() * 65f, x + 7f, 423f), blue)
            }
            text(c, "00:00", 310f, 438f, 7f)
            right(c, "24:00", 550f, 438f, 7f)
            section(c, s(R.string.lv_report_sensor_use), 470f)
            metric(c, s(R.string.lv_report_coverage), percent(summary.coverage.percent), 511f, width = 245f)
            metric(c, s(R.string.lv_report_days_with_data), summary.coverage.daysWithData.toString(), 547f, width = 245f)
            text(c, s(R.string.lv_report_last_14), 310f, 505f, 8f, color = gray, width = 250f)
            recentDates.forEachIndexed { i, day ->
                val from = maxOf(start, day.atStartOfDay(zone).toInstant().toEpochMilli())
                val until = minOf(end, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val coverage = if (until > from) StatsAnalytics.sensorCoverage(daily[day].orEmpty(), StatsDateRange(from, until)).percent else 0f
                val x = 312f + i * 17f
                rect(c, RectF(x, 595f - coverage * 0.7f, x + 11f, 595f), blue)
                text(c, day.dayOfMonth.toString(), x, 607f, 6f, color = gray, width = 17f)
            }
            section(c, s(R.string.lv_report_journal), 650f)
            val carbs = journal.filter { it.entryType == "carbs" && validAmount(it) }
            val insulin = journal.filter { it.entryType == "insulin" && validAmount(it) }
            fun recorded(entries: List<JournalEntryEntity>, suffix: String) =
                if (entries.isEmpty()) "-" else "${number((entries.sumOf { it.amount!!.toDouble() } / days).toFloat())} $suffix"
            metric(c, s(R.string.lv_report_carbs), recorded(carbs, "g"), 694f, width = 535f)
            metric(c, s(R.string.lv_report_insulin), recorded(insulin, s(R.string.lv_report_units)), 727f, width = 535f)
            text(c, s(R.string.lv_report_journal_note), 30f, 760f, 8f, color = gray)
            text(c, s(R.string.lv_report_journal_note_2), 30f, 774f, 8f, color = gray)
        }

        private fun validAmount(entry: JournalEntryEntity) = entry.amount?.let { it.isFinite() && it > 0f } == true

        private fun trends(c: Canvas) {
            section(c, s(R.string.lv_report_glucose), 100f)
            profile(c, RectF(75f, 145f, 520f, 410f))
            text(c, s(R.string.lv_report_percentiles), 75f, 450f, 8f, color = gray)
            section(c, s(R.string.lv_report_two_hour), 480f)
            for (i in 0 until 12) {
                val from = i * 2
                val values = points.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).hour in from until from + 2 }
                val x = 30f + (i % 6) * (535f / 6f)
                val top = 520f + (i / 6) * 55f
                text(c, "%02d-%02d".format(locale, from, from + 2), x + 5f, top, 8f, color = gray, width = 80f)
                text(c, if (values.isEmpty()) "-" else glucose(values.map { it.value }.average().toFloat()),
                    x + 5f, top + 20f, 13f, true, blue, 80f)
            }
            section(c, s(R.string.lv_report_recorded_trends), 650f)
            listOf("carbs", "insulin").forEachIndexed { row, type ->
                val entries = journal.filter { it.entryType == type && validAmount(it) }
                val sums = FloatArray(12)
                entries.forEach {
                    val hour = Instant.ofEpochMilli(it.timestamp).atZone(zone).hour
                    sums[hour / 2] += it.amount!! / days.toFloat()
                }
                val top = 688f + row * 54f
                text(c, s(if (row == 0) R.string.lv_report_carbs else R.string.lv_report_insulin), 30f, top, 8f, true, width = 535f)
                if (entries.isEmpty()) text(c, s(R.string.lv_report_no_data_short), 30f, top + 19f, 8f, color = gray)
                else sums.forEachIndexed { i, value ->
                    text(c, number(value), 30f + i * (535f / 12f), top + 19f, 8f, width = 43f)
                }
            }
            text(c, s(R.string.lv_report_journal_note), 30f, 790f, 7f, color = gray)
        }
    }
}
