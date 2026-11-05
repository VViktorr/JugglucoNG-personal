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
        weather: WidgetWeather? = null,
        includeBackground: Boolean = true
    ): Bitmap {
        val width = max(1, (widthDp * pxPerDp).roundToInt())
        val height = max(1, (heightDp * pxPerDp).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (includeBackground) drawBackground(canvas, width.toFloat(), height.toFloat(), config, snapshot)

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

    /** Draw text directly: detached TextViews may retain an empty/clipped layout. */
    fun renderClockHeader(
        config: WidgetConfig, snapshot: WidgetSnapshot, widthDp: Float, heightDp: Float,
        pxPerDp: Float, weather: WidgetWeather?, includeBackground: Boolean,
        valueText: String, reserveTrendArrowSpace: Boolean
    ): Bitmap {
        val bitmap = render(WidgetKind.CLOCK_WEATHER, config, snapshot, widthDp, heightDp,
            pxPerDp, weather, includeBackground)
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val color = if ((context.resources.configuration.uiMode and 0x30) == 0x20 ||
            config.background == WidgetBackground.RANGE) Color.WHITE else 0xFF1D1B20.toInt()
        val fontScale = context.resources.configuration.fontScale
        val mainSize = min(widthDp * .145f, heightDp * .46f) * config.glucoseScale / 100f * pxPerDp * fontScale
        val detailSize = min(widthDp * .042f, heightDp * .19f) * config.detailScale / 100f * pxPerDp * fontScale
        val hasWeather = config.showWeather && weather != null &&
            (config.showWeatherIcon || config.showTemperature)
        fun text(value: String, left: Float, right: Float, top: Float, bottom: Float,
                 size: Float, primary: Boolean, end: Boolean, tint: Int = color) {
            if (value.isBlank() || right <= left || bottom <= top) return
            val p = paint(size, tint, config, primary)
            // Include glyph overhangs and full font extents (accents/descenders),
            // leaving a pixel of breathing room even at reduced bitmap density.
            val bounds = Rect()
            fun extents(): Pair<Float, Float> {
                p.getTextBounds(value, 0, value.length, bounds)
                val fm = p.fontMetrics
                return min(fm.top, bounds.top.toFloat()) to max(fm.bottom, bounds.bottom.toFloat())
            }
            val availableW = (right - left - 2f).coerceAtLeast(1f)
            val availableH = (bottom - top - 2f).coerceAtLeast(1f)
            repeat(2) {
                val (a, b) = extents()
                val span = max(p.measureText(value), bounds.right.toFloat()) - min(0f, bounds.left.toFloat())
                p.textSize *= min(1f, min(availableW / max(1f, span), availableH / max(1f, b - a)))
            }
            val (a, b) = extents()
            p.textAlign = Paint.Align.LEFT
            val x = if (end) right - 1f - max(p.measureText(value), bounds.right.toFloat())
                else left + 1f - min(0f, bounds.left.toFloat())
            canvas.drawText(value, x, (top + bottom - a - b) / 2f, p)
        }
        val now = java.util.Date()
        val clock = android.text.format.DateFormat.getTimeFormat(context).format(now)
        val date = android.text.format.DateFormat.format("EEEE, d MMMM", now).toString()
        text(clock, 12f * pxPerDp, w * (if (hasWeather) .30f else .48f), 0f, h / 2f, mainSize, true, false)
        if (config.showDate) text(date, 14f * pxPerDp, w * .49f, h / 2f, h, detailSize, false, false)
        val rightStart = if (hasWeather) w * .68f + 8f * pxPerDp else w * .50f
        if (snapshot.hasFreshReading) {
            val arrowSize = min(w * .09f, h * .32f) * config.glucoseScale / 100f
            val endPadding = if (reserveTrendArrowSpace && config.showArrow && snapshot.rate.isFinite())
                max(38f * pxPerDp, w * .07f + arrowSize / 2f + 8f * pxPerDp) else 38f * pxPerDp
            val value = valueText.takeIf { it.isNotBlank() } ?: snapshot.valueText.takeIf { it.isNotBlank() } ?: "—"
            text(value, rightStart, w - endPadding, 0f, h / 2f, mainSize, true, true,
                glucoseTextColor(snapshot, config))
            val detail = buildList {
                if (config.showDelta) snapshot.delta?.let { add(deltaText(it, snapshot.isMmol)) }
                if (config.showUnit) add(snapshot.unitText)
                if (config.showTime) snapshot.current?.let {
                    add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)))
                }
            }.joinToString("  ")
            text(detail, w * .51f, w - 12f * pxPerDp, h / 2f, h, detailSize, false, true)
        } else {
            val status = snapshot.statusText?.takeIf { it.isNotBlank() } ?: context.getString(R.string.novalue)
            text(status, rightStart, w - 12f * pxPerDp, 0f, h, detailSize, false, true)
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
        // One explicit radius for every widget. Zero means the app draws a
        // rectangular surface and leaves any launcher masking entirely to Android.
        val radius = config.cornerRadiusDp.coerceIn(0, 24) * density
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

    fun glucoseTextColor(snapshot: WidgetSnapshot, config: WidgetConfig): Int = contentColor(snapshot, config)

    private fun contentColor(snapshot: WidgetSnapshot, config: WidgetConfig): Int {
        val (_, fg) = palette(snapshot)
        val value = snapshot.current?.primaryValue ?: return fg
        return if (config.rangeColors) rangeColor(value, snapshot) else fg
    }

    private fun configuredTypeface(config: WidgetConfig, bold: Boolean): Typeface {
        val weight = if (bold) config.primaryFontWeight else config.secondaryFontWeight
        return tk.glucodata.FontCatalog.typeface(context, config.fontFamily, weight, false)
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
            fontVariationSettings = "'wght' ${if (bold) config.primaryFontWeight else config.secondaryFontWeight}, 'wdth' 100"
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
        val textH = fm.bottom - fm.top
        if (textH > maxH && textH > 0f) {
            p.textSize *= maxH / textH
        }
        return p
    }

    private fun baselineCenter(p: Paint, cy: Float): Float {
        val fm = p.fontMetrics
        return cy - (fm.top + fm.bottom) / 2f
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
        val p = fitText(text, w * 0.9f, h * 0.5f, fg, config.detailScale, config, false)
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

        fun componentScale(component: Int): Int =
            component.coerceIn(50, 260)

        val weatherCenterX = (w * 0.44f - c.weatherShiftDp * density)
            .coerceIn(w * 0.27f, w * 0.54f)
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

            val weatherBase = componentScale(c.weatherScale)
            val iconScale = (weatherBase * c.weatherIconScale / 100f).roundToInt().coerceIn(45, 300)
            val tempScale = (weatherBase * c.temperatureScale / 100f).roundToInt().coerceIn(45, 300)
            val iconMaxW = w * 0.11f
            val tempMaxW = w * 0.14f
            val gap = 5f * density

            var iconW = 0f
            if (c.showWeatherIcon) {
                val iconPaint = fitText(symbol, iconMaxW, h * 0.44f, foreground, iconScale, c, false)
                iconW = iconPaint.measureText(symbol)
                val iconX = weatherCenterX - if (c.showTemperature) w * 0.045f else 0f
                canvas.drawText(symbol, iconX, baselineCenter(iconPaint, h * 0.34f), iconPaint)
            }

            if (c.showTemperature) {
                val temperature = "${weather.temperatureC}°"
                val tempPaint = fitText(temperature, tempMaxW, h * 0.42f, foreground, tempScale, c, false)
                val tempX = weatherCenterX + if (c.showWeatherIcon) max(iconW * 0.55f, gap) else 0f
                canvas.drawText(temperature, tempX, baselineCenter(tempPaint, h * 0.34f), tempPaint)
            }
        }

        val glucoseCenterX = w * 0.80f
        val glucoseText = if (s.hasFreshReading) s.valueText else "—"
        val glucoseColor = if (s.hasFreshReading) contentColor(s, c) else foreground
        val glucosePaint = fitText(
            glucoseText, w * 0.25f, h * 0.56f, glucoseColor,
            c.glucoseScale, c.copy(fontFamily = c.mainFontFamily), true
        )
        // Glucose value is a native TextView beside TextClock, sharing its exact font and row.

        if (c.showArrow && s.hasFreshReading && s.rate.isFinite()) {
            drawTrendArrow(
                canvas, w * 0.93f, h * 0.36f,
                min(w * 0.09f, h * 0.32f) * c.glucoseScale / 100f,
                s.rate, glucoseColor, s.isMmol
            )
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
            c.glucoseScale.coerceIn(50, 260),
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
                min(split * 0.24f, h * 0.30f) * c.glucoseScale / 100f,
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
                c.detailScale.coerceIn(50, 260),
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
            c.detailScale.coerceIn(50, 260),
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
            c.glucoseScale.coerceIn(50, 260),
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
                c.detailScale.coerceIn(50, 260),
                c,
                false
            )
            canvas.drawText(avgText, statsCenterX, baselineCenter(ap, h * 0.82f), ap)
        }
    }

    fun deltaText(delta: Float, isMmol: Boolean): String {
        val sign = if (delta > 0) "+" else if (abs(delta) < 0.05f) "±" else ""
        return if (isMmol) sign + String.format("%.1f", delta) else sign + delta.roundToInt()
    }
}

