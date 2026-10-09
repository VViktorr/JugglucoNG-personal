package tk.glucodata.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import tk.glucodata.Log
import tk.glucodata.MainActivity
import tk.glucodata.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sqrt

object WidgetUpdater {
    private fun weightStyle(weight: Int): Int = when {
        weight <= 300 -> R.style.WidgetTextWeight300
        weight <= 400 -> R.style.WidgetTextWeight400
        weight <= 500 -> R.style.WidgetTextWeight500
        weight <= 600 -> R.style.WidgetTextWeight600
        else -> R.style.WidgetTextWeight700
    }
    private const val LOG_ID="WidgetUpdater"
    private val executor=Executors.newSingleThreadExecutor { r -> Thread(r,"home-widgets").apply { priority=Thread.NORM_PRIORITY-1 } }
    private val weatherExecutor=Executors.newSingleThreadExecutor { r -> Thread(r,"widget-weather").apply { priority=Thread.NORM_PRIORITY-1 } }
    private val pending=AtomicBoolean(false)
    private val rerunRequested=AtomicBoolean(false)
    private val weatherPending=AtomicBoolean(false)
    private val pendingCallbacks=java.util.concurrent.ConcurrentLinkedQueue<()->Unit>()

    fun requestUpdateAll(context:Context,done:(()->Unit)?=null){
        done?.let(pendingCallbacks::add)
        val app=context.applicationContext
        if(!pending.compareAndSet(false,true)) {
            // A glucose update that arrives while bitmaps are being rendered must not be
            // dropped. Remember it and run one more pass with the newest snapshot.
            rerunRequested.set(true)
            return
        }
        executor.execute {
            try {
                val manager=AppWidgetManager.getInstance(app)
                val targets=WidgetKind.entries.associateWith { manager.getAppWidgetIds(ComponentName(app,it.provider)) }
                    .filterValues { it.isNotEmpty() }
                if(targets.isNotEmpty()) render(app,manager,targets)
            } catch(t:Throwable){ Log.stack(LOG_ID,"requestUpdateAll",t) }
            finally {
                val rerun=rerunRequested.getAndSet(false)
                pending.set(false)
                while(true){
                    val callback=pendingCallbacks.poll() ?: break
                    try { callback() } catch(_:Throwable) {}
                }
                if(rerun) requestUpdateAll(app)
            }
        }
    }

    fun updateAllOfKind(context:Context,kind:WidgetKind,done:(()->Unit)?=null){
        val app=context.applicationContext
        val ids=AppWidgetManager.getInstance(app).getAppWidgetIds(ComponentName(app,kind.provider))
        update(app,kind,ids,done)
    }

    fun update(context:Context,kind:WidgetKind,ids:IntArray,done:(()->Unit)?=null){
        val app=context.applicationContext
        executor.execute {
            try { if(ids.isNotEmpty()) render(app,AppWidgetManager.getInstance(app),mapOf(kind to ids)) }
            catch(t:Throwable){ Log.stack(LOG_ID,"update $kind",t) }
            finally{ done?.invoke() }
        }
    }

    private fun render(context:Context,manager:AppWidgetManager,targets:Map<WidgetKind,IntArray>){
        val configs=targets.keys.associateWith { WidgetConfigStore.load(context,it) }
        val now=System.currentTimeMillis()
        var history=30*60_000L
        configs.forEach { (kind,c) ->
            if(kind.hasGraph) history=maxOf(history,c.graphHours*3_600_000L)
            if(kind.hasStats) history=maxOf(history,when(c.statsPeriod){
                WidgetStatsPeriod.TODAY -> now-java.util.Calendar.getInstance().apply{timeInMillis=now;set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
                WidgetStatsPeriod.DAY -> 24*3_600_000L
                WidgetStatsPeriod.WEEK -> 7*24*3_600_000L
            })
        }
        val snapshot=WidgetDataSource.load(context,history)
        val wantsWeather = targets.containsKey(WidgetKind.CLOCK_WEATHER) &&
            configs[WidgetKind.CLOCK_WEATHER]?.showWeather == true
        val weather = if (wantsWeather) WidgetWeatherSource.cached(context) else null
        val renderer=WidgetRenderer(context)
        val description=renderer.describe(snapshot)
        targets.forEach { (kind,ids) ->
            val config=configs.getValue(kind)
            ids.forEach { id ->
                try {
                    manager.updateAppWidget(id,buildViews(context,manager,kind,id){w,h,px->
                        RemoteViews(
                            context.packageName,
                            if (kind == WidgetKind.CLOCK_WEATHER) {
                                when {
                                    config.mainFontFamily == WidgetFontFamily.IBM_PLEX && config.detailFontFamily == WidgetFontFamily.IBM_PLEX ->
                                        R.layout.home_widget_clock_weather_ibm
                                    config.mainFontFamily == WidgetFontFamily.SYSTEM && config.detailFontFamily == WidgetFontFamily.IBM_PLEX ->
                                        R.layout.home_widget_clock_weather_system_main_ibm_detail
                                    config.mainFontFamily == WidgetFontFamily.IBM_PLEX && config.detailFontFamily == WidgetFontFamily.SYSTEM ->
                                        R.layout.home_widget_clock_weather_ibm_main_system_detail
                                    else -> R.layout.home_widget_clock_weather
                                }
                            } else {
                                R.layout.home_widget
                            }
                        ).apply{
                            if(kind==WidgetKind.CLOCK_WEATHER){
                                val clockSize=min(w*0.145f,h*0.46f)*config.glucoseScale/100f
                                val dateSize=min(w*0.042f,h*0.19f)*config.detailScale/100f
                                val textColor=if((context.resources.configuration.uiMode and 0x30)==0x20 || config.background==WidgetBackground.RANGE) android.graphics.Color.WHITE else 0xFF1D1B20.toInt()
                                setTextViewTextSize(R.id.home_widget_clock,android.util.TypedValue.COMPLEX_UNIT_SP,clockSize)
                                setTextColor(R.id.home_widget_clock,textColor)
                                setTextViewTextSize(R.id.home_widget_date,android.util.TypedValue.COMPLEX_UNIT_SP,dateSize)
                                setTextColor(R.id.home_widget_date,textColor)
                                setViewVisibility(R.id.home_widget_date,if(config.showDate) android.view.View.VISIBLE else android.view.View.GONE)
                            }
                            setImageViewBitmap(R.id.home_widget_image,renderer.render(kind,config,snapshot,w,h,px,weather))
                            setContentDescription(R.id.home_widget_image,description)
                            setOnClickPendingIntent(R.id.home_widget_root,openAppIntent(context,id))
                        }
                    })
                } catch(t:Throwable){ Log.stack(LOG_ID,"render $kind $id",t) }
            }
        }
        if (wantsWeather) scheduleWeatherRefresh(context)
    }

    private fun scheduleWeatherRefresh(context: Context) {
        if (!WidgetWeatherSource.needsRefresh(context)) return
        if (!weatherPending.compareAndSet(false, true)) return
        val app = context.applicationContext
        weatherExecutor.execute {
            try {
                val before = WidgetWeatherSource.cached(app)?.observedAt ?: 0L
                val loaded = WidgetWeatherSource.load(app)
                if (loaded != null && loaded.observedAt > before) {
                    requestUpdateAll(app)
                }
            } catch (t: Throwable) {
                Log.stack(LOG_ID, "weather refresh", t)
            } finally {
                weatherPending.set(false)
            }
        }
    }

    private fun buildViews(context:Context,manager:AppWidgetManager,kind:WidgetKind,id:Int,build:(Float,Float,Float)->RemoteViews):RemoteViews{
        val sizes=widgetSizes(manager.getAppWidgetOptions(id)).ifEmpty{listOf(SizeF(kind.defaultWidthDp,kind.defaultHeightDp))}
        val metrics=context.resources.displayMetrics
        val density=metrics.density
        // RemoteViews transports every responsive bitmap to the launcher. Four full-density
        // variants can become a multi-megabyte update and some launchers intermittently drop
        // it, leaving the widget blank until a resize/long-press forces another update.
        // Keep the complete update comfortably small while retaining enough pixels for text.
        val screenPixels=metrics.widthPixels.toFloat()*metrics.heightPixels
        val budget=min(screenPixels*0.45f,600_000f)
        val total=sizes.sumOf{(it.width*it.height*density*density).toDouble()}.toFloat()
        val px=if(total>budget) density*sqrt(budget/total) else density
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S && sizes.size>1) return RemoteViews(sizes.associateWith{build(it.width,it.height,px)})
        if(sizes.size>=2) return RemoteViews(build(sizes.last().width,sizes.last().height,px),build(sizes.first().width,sizes.first().height,px))
        return build(sizes.first().width,sizes.first().height,px)
    }

    private fun widgetSizes(options:Bundle?):List<SizeF>{
        if(options==null) return emptyList()
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){
            @Suppress("DEPRECATION")
            val sizes=options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if(!sizes.isNullOrEmpty()){
                val valid=sizes.filter{it.width>0&&it.height>0}.distinct().sortedBy{it.width*it.height}
                if(valid.size<=2) return valid
                // Smallest and largest cover compact/expanded launcher layouts without
                // packing four independent bitmaps into every RemoteViews transaction.
                return listOf(valid.first(),valid.last())
            }
        }
        val minW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if(minW<=0||maxH<=0) return emptyList()
        return listOf(SizeF(minW.toFloat(),maxH.toFloat()),SizeF(maxOf(maxW,minW).toFloat(),maxOf(1,min(minH,maxH)).toFloat())).distinct()
    }

    private fun openAppIntent(context:Context,id:Int):PendingIntent{
        val flags=PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context,id,Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),flags)
    }
}
