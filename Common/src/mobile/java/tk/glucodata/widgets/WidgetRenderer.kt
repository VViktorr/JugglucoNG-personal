package tk.glucodata.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import tk.glucodata.NotificationChartDrawer
import tk.glucodata.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WidgetRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density.coerceAtLeast(1f)

    fun render(
        kind: WidgetKind,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        widthDp: Float,
        heightDp: Float,
        pxPerDp: Float,
        weather: WidgetWeather? = null
    ): Bitmap {
        val width = max(1, (widthDp * pxPerDp).roundToInt())
        val height = max(1, (heightDp * pxPerDp).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width.toFloat(), height.toFloat(), config, snapshot)

        snapshot.statusText?.let {
            if (!snapshot.hasFreshReading && kind != WidgetKind.CLOCK_WEATHER) {
                drawStatus(canvas, width.toFloat(), height.toFloat(), it, config)
                return bitmap
            }
        }

        when (kind) {
            WidgetKind.RANGE -> drawRange(canvas, width.toFloat(), height.toFloat(), config, snapshot)
            WidgetKind.CLOCK_WEATHER -> drawClockWeather(
                canvas,
                width.toFloat(),
                height.toFloat(),
                config,
                snapshot,
                weather
            )
        }
        return bitmap
    }

    fun describe(snapshot: WidgetSnapshot): String =
        snapshot.statusText ?: buildString {
            append(snapshot.valueText)
            if (snapshot.isMmol) append(" mmol/L") else append(" mg/dL")
            snapshot.delta?.let {
                append(", ")
                append(deltaText(it, snapshot.isMmol))
            }
        }

    private fun palette(snapshot: WidgetSnapshot): Pair<Int, Int> {
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        return if (dark) {
            0xFF1C1B1F.toInt() to Color.WHITE
        } else {
            0xFFF7F2FA.toInt() to 0xFF1D1B20.toInt()
        }
    }

    private fun rangeColor(value: Float, snapshot: WidgetSnapshot): Int = when {
        value < snapshot.range.veryLow -> 0xFFB3261E.toInt()
        value < snapshot.range.low -> 0xFFE65100.toInt()
        value <= snapshot.range.high -> 0xFF2E7D32.toInt()
        value <= snapshot.range.veryHigh -> 0xFFF57C00.toInt()
        else -> 0xFFB3261E.toInt()
    }

    private fun drawBackground(
        canvas: Canvas,
        w: Float,
        h: Float,
        config: WidgetConfig,
        snapshot: WidgetSnapshot
    ) {
        if (config.background == WidgetBackground.TRANSPARENT || config.opacity <= 0) return
        val (surface, _) = palette(snapshot)
        val base =
            if (config.background == WidgetBackground.RANGE && snapshot.current != null) {
                rangeColor(snapshot.current.primaryValue, snapshot)
            } else {
                surface
            }
        val color = Color.argb(
            (255 * config.opacity / 100f).roundToInt(),
            Color.red(base),
            Color.green(base),
            Color.blue(base)
        )
        // Widget corner radius must not grow with widget height. Percentage-based
        // radii created the large cut-outs visible after resizing a widget.
        val systemRadiusId = context.resources.getIdentifier(
            "system_app_widget_background_radius",
            "dimen",
            "android"
        )
        val systemRadius = if (systemRadiusId != 0) {
            context.resources.getDimension(systemRadiusId)
        } else {
            24f * density
        }
        val radius = when (config.shape) {
            WidgetShape.PILL -> min(w, h) / 2f
            WidgetShape.ROUND -> 24f * density
            WidgetShape.SQUARE -> 6f * density
            WidgetShape.SYSTEM -> systemRadius
        }
        canvas.drawRoundRect(
            0f,
            0f,
            w,
            h,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        )
    }

    private fun contentColor(snapshot: WidgetSnapshot, config: WidgetConfig): Int {
        val (_, fg) = palette(snapshot)
        val value = snapshot.current?.primaryValue ?: return fg
        return if (config.rangeColors) rangeColor(value, snapshot) else fg
    }

    private fun configuredTypeface(config: WidgetConfig, bold: Boolean): Typeface {
        val weight = config.fontWeight
        return try {
            val base = if (config.fontFamily == WidgetFontFamily.IBM_PLEX) {
                ResourcesCompat.getFont(context, R.font.ibm_plex_sans_var)
                    ?: Typeface.create("sans-serif", Typeface.NORMAL)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else base
        } catch (_: Throwable) {
            val fallback = Typeface.create("sans-serif", Typeface.NORMAL)
            if (Build.VERSION.SDK_INT >= 28) Typeface.create(fallback, weight, false) else fallback
        }
    }

    private fun paint(
        size: Float,
        color: Int,
        config: WidgetConfig,
        bold: Boolean = false
    ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        textAlign = Paint.Align.CENTER
        typeface = configuredTypeface(config, bold)
        if (Build.VERSION.SDK_INT >= 26 && config.fontFamily == WidgetFontFamily.IBM_PLEX) {
            fontVariationSettings = "'wght' ${config.fontWeight}, 'wdth' 100"
        }
    }

    private fun fitText(
        text: String,
        maxW: Float,
        maxH: Float,
        color: Int,
        scale: Int,
        config: WidgetConfig,
        bold: Boolean = true
    ): Paint {
        var size = maxH * 0.82f * scale / 100f
        val p = paint(size, color, config, bold)
        val measured = p.measureText(text)
        if (measured > maxW && measured > 0f) {
            size *= maxW / measured
            p.textSize = size
        }
        val fm = p.fontMetrics
        val textH = fm.descent - fm.ascent
        if (textH > maxH && textH > 0f) {
            p.textSize *= maxH / textH
        }
        return p
    }

    private fun baselineCenter(p: Paint, cy: Float): Float {
        val fm = p.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    private fun drawStatus(
        canvas: Canvas,
        w: Float,
        h: Float,
        text: String,
        config: WidgetConfig
    ) {
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        val fg = if (dark) Color.WHITE else 0xFF1D1B20.toInt()
        val p = fitText(text, w * 0.9f, h * 0.5f, fg, config.textScale, config, false)
        val lines = if (p.measureText(text) <= w * 0.9f) listOf(text) else splitStatus(text)
        val lineH = p.textSize * 1.15f
        var y = h / 2f - (lines.size - 1) * lineH / 2f
        lines.forEach {
            canvas.drawText(it, w / 2f, baselineCenter(p, y), p)
            y += lineH
        }
    }

    private fun splitStatus(text: String): List<String> {
        val words = text.split(' ')
        if (words.size < 3) return listOf(text)
        val mid = words.size / 2
        return listOf(
            words.take(mid).joinToString(" "),
            words.drop(mid).joinToString(" ")
        )
    }

    private fun drawTrendArrow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxSize: Float,
        rate: Float,
        color: Int,
        isMmol: Boolean
    ) {
        if (!rate.isFinite() || maxSize <= 0f) return
        val requestedScale = (maxSize / (30f * density)).coerceIn(0.7f, 2.2f)
        val arrow = try {
            NotificationChartDrawer.drawArrow(context, rate, isMmol, color, requestedScale)
        } catch (_: Throwable) {
            null
        } ?: return
        val rawW = arrow.width.toFloat().coerceAtLeast(1f)
        val rawH = arrow.height.toFloat().coerceAtLeast(1f)
        val fit = min(1f, maxSize / max(rawW, rawH))
        val drawW = max(1, (rawW * fit).roundToInt())
        val drawH = max(1, (rawH * fit).roundToInt())
        val left = (cx - drawW / 2f).roundToInt()
        val top = (cy - drawH / 2f).roundToInt()
        canvas.drawBitmap(
            arrow,
            null,
            Rect(left, top, left + drawW, top + drawH),
            null
        )
    }

    private fun drawClockWeather(
        canvas: Canvas,
        w: Float,
        h: Float,
        c: WidgetConfig,
        s: WidgetSnapshot,
        weather: WidgetWeather?
    ) {
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        val foreground =
            if (dark || c.background == WidgetBackground.RANGE) Color.WHITE else 0xFF1D1B20.toInt()
        val compact = w / h >= 3.0f
        val clockX = w * 0.18f
        val weatherX = (w * 0.47f - c.weatherShiftDp * density)
            .coerceIn(w * 0.30f, w * 0.52f)
        val glucoseCenterX = w * 0.79f
        fun componentScale(component: Int): Int =
            (c.textScale * component / 100f).roundToInt().coerceIn(55, 220)

        val now = java.util.Date()
        val timeText = android.text.format.DateFormat.getTimeFormat(context).format(now)
        val clockPaint = fitText(
            timeText,
            w * 0.31f,
            if (compact) h * 0.52f else h * 0.44f,
            foreground,
            componentScale(c.timeScale),
            c,
            true
        )
        canvas.drawText(
            timeText,
            clockX,
            baselineCenter(clockPaint, if (c.showDate) h * 0.40f else h * 0.50f),
            clockPaint
        )

        if (c.showDate) {
            val dateText = java.text.SimpleDateFormat(
                "EEEE, d MMMM",
                java.util.Locale.getDefault()
            ).format(now)
            val datePaint = fitText(
                dateText,
                w * 0.33f,
                h * 0.18f,
                foreground,
                componentScale((c.timeScale * 90f / 100f).roundToInt()),
                c,
                false
            )
            canvas.drawText(dateText, clockX, baselineCenter(datePaint, h * 0.76f), datePaint)
        }

        if (c.showWeather && weather != null) {
            val symbol = when (weather.weatherCode) {
                0 -> "☀"
                1, 2 -> "⛅"
                3 -> "☁"
                in 45..48 -> "≋"
                in 51..67, in 80..82 -> "☂"
                in 71..77, in 85..86 -> "❄"
                in 95..99 -> "ϟ"
                else -> "☁"
            }
            val label =
                (if (c.showWeatherIcon) "$symbol " else "") +
                    (if (c.showTemperature) "${weather.temperatureC}°" else "")
            if (label.isNotBlank()) {
                val p = fitText(
                    label,
                    w * 0.22f,
                    h * 0.38f,
                    foreground,
                    componentScale(c.weatherScale),
                    c,
                    false
                )
                canvas.drawText(label, weatherX, baselineCenter(p, h * 0.48f), p)
            }
        }

        val glucoseText = if (s.hasFreshReading) s.valueText else "—"
        val glucoseColor = if (s.hasFreshReading) contentColor(s, c) else foreground
        val valueMaxW = w * 0.23f
        val glucosePaint = fitText(
            glucoseText,
            valueMaxW,
            h * 0.58f,
            glucoseColor,
            componentScale(c.glucoseScale),
            c,
            true
        )
        canvas.drawText(
            glucoseText,
            glucoseCenterX - w * 0.025f,
            baselineCenter(glucosePaint, h * 0.40f),
            glucosePaint
        )

        if (c.showArrow && s.hasFreshReading && s.rate.isFinite()) {
            drawTrendArrow(
                canvas,
                w * 0.93f,
                h * 0.38f,
                min(w * 0.085f, h * 0.32f),
                s.rate,
                glucoseColor,
                s.isMmol
            )
        }

        val detail = if (!s.hasFreshReading) {
            s.statusText
        } else {
            buildList {
                if (c.showDelta) s.delta?.let { add(deltaText(it, s.isMmol)) }
                if (c.showUnit) add(s.unitText)
                if (c.showTime) s.current?.let {
                    add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)))
                }
            }.joinToString("  ")
        }
        if (!detail.isNullOrBlank()) {
            val p = fitText(detail, w * 0.34f, h * 0.17f, foreground, 90, c, false)
            canvas.drawText(detail, glucoseCenterX, baselineCenter(p, h * 0.76f), p)
        }
    }

    private fun drawRange(
        canvas: Canvas,
        w: Float,
        h: Float,
        c: WidgetConfig,
        s: WidgetSnapshot
    ) {
        val split = w * 0.46f
        val glucoseColor = contentColor(s, c)
        val valueCenterX = split * 0.42f
        val valuePaint = fitText(
            s.valueText,
            split * 0.62f,
            h * 0.50f,
            glucoseColor,
            c.textScale,
            c,
            true
        )
        canvas.drawText(
            s.valueText,
            valueCenterX,
            baselineCenter(valuePaint, h * 0.40f),
            valuePaint
        )

        if (c.showArrow && s.rate.isFinite()) {
            drawTrendArrow(
                canvas,
                split * 0.82f,
                h * 0.38f,
                min(split * 0.24f, h * 0.30f),
                s.rate,
                glucoseColor,
                s.isMmol
            )
        }

        val details = buildList {
            if (c.showDelta) s.delta?.let { add(deltaText(it, s.isMmol)) }
            if (c.showUnit) add(s.unitText)
            if (c.showTime) s.current?.let {
                add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)))
            }
        }.joinToString("  ")
        if (details.isNotBlank()) {
            val detailPaint = fitText(
                details,
                split * 0.86f,
                h * 0.15f,
                palette(s).second,
                90,
                c,
                false
            )
            canvas.drawText(
                details,
                split / 2f,
                baselineCenter(detailPaint, h * 0.78f),
                detailPaint
            )
        }

        val stats = s.stats(c.statsPeriod)
        val title =
            if (c.statsPeriod == WidgetStatsPeriod.TODAY) "TIR dziś"
            else if (c.statsPeriod == WidgetStatsPeriod.DAY) "TIR 24h"
            else "TIR 7d"
        val statsCenterX = split + (w - split) / 2f
        val titlePaint = fitText(
            title,
            w - split - 14 * density,
            h * 0.16f,
            palette(s).second,
            100,
            c,
            false
        )
        canvas.drawText(title, statsCenterX, baselineCenter(titlePaint, h * 0.20f), titlePaint)

        val tir = (stats?.inRange?.times(100f))?.roundToInt()
        val tirText = tir?.let { "$it%" } ?: "—"
        val tirPaint = fitText(
            tirText,
            (w - split) * 0.82f,
            h * 0.46f,
            0xFF2E7D32.toInt(),
            c.textScale,
            c,
            true
        )
        canvas.drawText(tirText, statsCenterX, baselineCenter(tirPaint, h * 0.52f), tirPaint)

        stats?.let {
            val avg =
                if (s.isMmol) String.format("%.1f", it.average) else it.average.roundToInt().toString()
            val avgText = "średnia $avg"
            val ap = fitText(
                avgText,
                w - split - 14 * density,
                h * 0.15f,
                palette(s).second,
                100,
                c,
                false
            )
            canvas.drawText(avgText, statsCenterX, baselineCenter(ap, h * 0.82f), ap)
        }
    }

    private fun deltaText(delta: Float, isMmol: Boolean): String {
        val sign = if (delta > 0) "+" else if (abs(delta) < 0.05f) "±" else ""
        return if (isMmol) sign + String.format("%.1f", delta) else sign + delta.roundToInt()
    }
}
