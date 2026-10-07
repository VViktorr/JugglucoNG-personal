package tk.glucodata.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import tk.glucodata.NotificationChartDrawer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class WidgetRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density.coerceAtLeast(1f)

    fun render(kind: WidgetKind, config: WidgetConfig, snapshot: WidgetSnapshot, widthDp: Float, heightDp: Float, pxPerDp: Float, weather: WidgetWeather? = null): Bitmap {
        val width = max(1, (widthDp * pxPerDp).roundToInt())
        val height = max(1, (heightDp * pxPerDp).roundToInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas, width.toFloat(), height.toFloat(), config, snapshot)
        if (kind == WidgetKind.CLOCK_WEATHER) {
            drawClockWeather(canvas, width.toFloat(), height.toFloat(), config, snapshot, weather)
            return bitmap
        }
        snapshot.statusText?.let {
            if (!snapshot.hasFreshReading) {
                drawStatus(canvas, width.toFloat(), height.toFloat(), it)
                return bitmap
            }
        }
        when (kind) {
            WidgetKind.DIAL -> drawDial(canvas, width.toFloat(), height.toFloat(), config, snapshot)
            WidgetKind.RANGE -> drawRange(canvas, width.toFloat(), height.toFloat(), config, snapshot)
            WidgetKind.CLOCK_WEATHER -> drawClockWeather(canvas, width.toFloat(), height.toFloat(), config, snapshot, weather)
        }
        return bitmap
    }

    fun describe(snapshot: WidgetSnapshot): String =
        snapshot.statusText ?: buildString {
            append(snapshot.valueText)
            if (snapshot.isMmol) append(" mmol/L") else append(" mg/dL")
            snapshot.delta?.let { append(", "); append(deltaText(it, snapshot.isMmol)) }
        }

    private fun palette(snapshot: WidgetSnapshot): Pair<Int,Int> {
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        return if (dark) 0xFF1C1B1F.toInt() to Color.WHITE else 0xFFF7F2FA.toInt() to 0xFF1D1B20.toInt()
    }

    private fun rangeColor(value: Float, snapshot: WidgetSnapshot): Int = when {
        value < snapshot.range.veryLow -> 0xFFB3261E.toInt()
        value < snapshot.range.low -> 0xFFE65100.toInt()
        value <= snapshot.range.high -> 0xFF2E7D32.toInt()
        value <= snapshot.range.veryHigh -> 0xFFF57C00.toInt()
        else -> 0xFFB3261E.toInt()
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float, config: WidgetConfig, snapshot: WidgetSnapshot) {
        if (config.background == WidgetBackground.TRANSPARENT || config.opacity <= 0) return
        val (surface, _) = palette(snapshot)
        val base = if (config.background == WidgetBackground.RANGE && snapshot.current != null)
            rangeColor(snapshot.current.primaryValue, snapshot) else surface
        val color = Color.argb((255 * config.opacity / 100f).roundToInt(), Color.red(base), Color.green(base), Color.blue(base))
        val radius = when (config.shape) {
            WidgetShape.PILL -> min(w,h)/2f
            WidgetShape.ROUND -> min(w,h)*0.22f
            WidgetShape.SQUARE -> 6f*density
            WidgetShape.SYSTEM -> 22f*density
        }
        canvas.drawRoundRect(0f,0f,w,h,radius,radius,Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color })
    }

    private fun contentColor(snapshot: WidgetSnapshot, config: WidgetConfig): Int {
        val (_, fg) = palette(snapshot)
        val value = snapshot.current?.primaryValue ?: return fg
        return if (config.rangeColors) rangeColor(value, snapshot) else fg
    }

    private fun paint(size: Float, color: Int, bold: Boolean=false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize=size; this.color=color; textAlign=Paint.Align.CENTER
        typeface=Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun fitText(text:String, maxW:Float, maxH:Float, color:Int, scale:Int, bold:Boolean=true): Paint {
        var size=maxH * 0.82f * scale / 100f
        val p=paint(size,color,bold)
        val measured=p.measureText(text)
        if(measured>maxW && measured>0f){ size*=maxW/measured; p.textSize=size }
        return p
    }

    private fun baselineCenter(p:Paint, cy:Float):Float {
        val fm=p.fontMetrics
        return cy-(fm.ascent+fm.descent)/2f
    }

    private fun drawStatus(canvas:Canvas,w:Float,h:Float,text:String){
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        val fg = if (dark) Color.WHITE else 0xFF1D1B20.toInt()
        val p=fitText(text,w*0.88f,h*0.44f,fg,100,false)
        val lines=if(p.measureText(text)<=w*0.88f) listOf(text) else splitStatus(text)
        val lineH=p.textSize*1.15f
        var y=h/2f-(lines.size-1)*lineH/2f
        lines.forEach { canvas.drawText(it,w/2f,baselineCenter(p,y),p); y+=lineH }
    }

    private fun splitStatus(text:String):List<String>{
        val words=text.split(' ')
        if(words.size<3) return listOf(text)
        val mid=words.size/2
        return listOf(words.take(mid).joinToString(" "), words.drop(mid).joinToString(" "))
    }

    private fun drawClockWeather(canvas: Canvas, w: Float, h: Float, c: WidgetConfig, s: WidgetSnapshot, weather: WidgetWeather?) {
        val dark = (context.resources.configuration.uiMode and 0x30) == 0x20
        val foreground = if (dark || c.background == WidgetBackground.RANGE) Color.WHITE else 0xFF1D1B20.toInt()
        // Time and date are Android TextClock overlays on the left.
        // Weather stays close to the clock, while glucose gets the full right side
        // and uses the same strong visual hierarchy as the small glucose widget.
        val glucoseCenterX = w * 0.80f
        val glucoseText = if (s.hasFreshReading) s.valueText else "—"
        val glucoseColor = if (s.hasFreshReading) contentColor(s,c) else foreground
        val glucosePaint = fitText(
            glucoseText,
            w * 0.24f,
            h * 0.62f,
            glucoseColor,
            c.textScale,
            true
        )
        canvas.drawText(
            glucoseText,
            glucoseCenterX,
            baselineCenter(glucosePaint, h * 0.40f),
            glucosePaint
        )

        if (c.showArrow && s.hasFreshReading && s.rate.isFinite()) {
            drawArrow(
                canvas,
                glucoseCenterX + w * 0.105f,
                h * 0.36f,
                h * 0.25f,
                s.rate,
                glucoseColor
            )
        }

        val detail = if (!s.hasFreshReading) {
            s.statusText
        } else {
            buildList {
                if (c.showDelta) s.delta?.let { add(deltaText(it,s.isMmol)) }
                if (c.showUnit) add(s.unitText)
                if (c.showTime) s.current?.let {
                    add(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)))
                }
            }.joinToString("  ")
        }
        if (!detail.isNullOrBlank()) {
            val p = fitText(detail,w*0.29f,h*0.14f,foreground,90,false)
            canvas.drawText(detail,glucoseCenterX,h*0.73f,p)
        }

        // Weather sits between the clock/date block and glucose, slightly raised.
        val weatherX = w * 0.42f
        if(c.showWeather && weather != null) {
            val symbol = when(weather.weatherCode) {
                0 -> "☀"
                1,2 -> "⛅"
                3 -> "☁"
                in 45..48 -> "≋"
                in 51..67, in 80..82 -> "☂"
                in 71..77, in 85..86 -> "❄"
                in 95..99 -> "ϟ"
                else -> "☁"
            }
            val label = (if(c.showWeatherIcon) "$symbol " else "") +
                (if(c.showTemperature) "${weather.temperatureC}°" else "")
            if(label.isNotBlank()){
                val p=fitText(label,w*0.18f,h*0.28f,foreground,100,false)
                canvas.drawText(label,weatherX,baselineCenter(p,h*0.34f),p)
            }
        }
    }

    private fun drawDial(canvas:Canvas,w:Float,h:Float,c:WidgetConfig,s:WidgetSnapshot){
        val side=min(w,h); val cx=w/2f; val cy=h/2f; val r=side*0.38f
        val track=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeWidth=side*0.07f;strokeCap=Paint.Cap.ROUND;color=0x44333333}
        canvas.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),135f,270f,false,track)
        s.current?.let { cur ->
            val minV=if(s.isMmol) 2f else 40f; val maxV=if(s.isMmol) 20f else 360f
            val frac=((cur.primaryValue-minV)/(maxV-minV)).coerceIn(0f,1f)
            track.color=contentColor(s,c)
            canvas.drawArc(RectF(cx-r,cy-r,cx+r,cy+r),135f,270f*frac,false,track)
            val angle=Math.toRadians((135f+270f*frac).toDouble())
            canvas.drawCircle(cx+r*cos(angle).toFloat(),cy+r*sin(angle).toFloat(),side*0.055f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=contentColor(s,c)})
        }
        val p=fitText(s.valueText,side*0.58f,side*0.28f,contentColor(s,c),c.textScale,true)
        canvas.drawText(s.valueText,cx,baselineCenter(p,cy),p)
        if(c.showUnit){
            val up=fitText(s.unitText,side*0.55f,side*0.09f,palette(s).second,100,false)
            canvas.drawText(s.unitText,cx,cy-side*0.15f,up)
        }
        if(c.showTime) s.current?.let {
            val tp=fitText(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)),side*0.5f,side*0.09f,palette(s).second,100,false)
            canvas.drawText(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)),cx,cy+side*0.23f,tp)
        }
    }

    private fun drawRange(canvas:Canvas,w:Float,h:Float,c:WidgetConfig,s:WidgetSnapshot){
        val split=w*0.42f
        val p=fitText(s.valueText,split*0.82f,h*0.38f,contentColor(s,c),c.textScale,true)
        canvas.drawText(s.valueText,split/2f,baselineCenter(p,h*0.43f),p)
        if(c.showTime) s.current?.let{
            val tp=fitText(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)),split*0.7f,h*0.11f,palette(s).second,100,false)
            canvas.drawText(android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(it.timeMillis)),split/2f,h*0.78f,tp)
        }
        val stats=s.stats(c.statsPeriod)
        val title=if(c.statsPeriod==WidgetStatsPeriod.TODAY) "TIR dziś" else if(c.statsPeriod==WidgetStatsPeriod.DAY) "TIR 24h" else "TIR 7d"
        val titlePaint=fitText(title,w-split-20*density,h*0.14f,palette(s).second,100,false)
        canvas.drawText(title,split+(w-split)/2f,h*0.22f,titlePaint)
        val tir=(stats?.inRange?.times(100f))?.roundToInt()
        val tirText=tir?.let{"$it%"} ?: "—"
        val tirPaint=fitText(tirText,(w-split)*0.75f,h*0.38f,0xFF2E7D32.toInt(),c.textScale,true)
        canvas.drawText(tirText,split+(w-split)/2f,baselineCenter(tirPaint,h*0.53f),tirPaint)
        stats?.let{
            val avg=if(s.isMmol) String.format("%.1f",it.average) else it.average.roundToInt().toString()
            val ap=fitText("średnia $avg",w-split-20*density,h*0.12f,palette(s).second,100,false)
            canvas.drawText("średnia $avg",split+(w-split)/2f,h*0.82f,ap)
        }
    }

    private fun deltaText(delta:Float,isMmol:Boolean):String{
        val sign=if(delta>0) "+" else if(abs(delta)<0.05f) "±" else ""
        return if(isMmol) sign+String.format("%.1f",delta) else sign+delta.roundToInt()
    }

    private fun drawArrow(canvas:Canvas,cx:Float,cy:Float,size:Float,rate:Float,color:Int){
        val rotation=(-rate*22f).coerceIn(-85f,85f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;style=Paint.Style.STROKE;strokeWidth=size*0.12f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        val path=Path().apply{
            moveTo(cx-size*0.35f,cy); lineTo(cx+size*0.35f,cy)
            moveTo(cx+size*0.12f,cy-size*0.22f); lineTo(cx+size*0.35f,cy); lineTo(cx+size*0.12f,cy+size*0.22f)
        }
        canvas.save(); canvas.rotate(rotation,cx,cy); canvas.drawPath(path,p); canvas.restore()
    }
}
