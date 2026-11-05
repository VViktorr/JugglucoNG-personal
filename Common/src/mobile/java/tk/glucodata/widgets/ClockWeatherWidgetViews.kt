package tk.glucodata.widgets

import android.content.Context
import android.widget.RemoteViews
import tk.glucodata.R

/** Shared bitmap header: launcher font restrictions cannot replace its typefaces. */
object ClockWeatherWidgetViews {
    fun create(
        context: Context,
        config: WidgetConfig,
        snapshot: WidgetSnapshot,
        w: Float,
        h: Float,
        pxPerDp: Float,
        weather: WidgetWeather?,
        includeBackground: Boolean = true,
        valueText: String = snapshot.valueText,
        reserveTrendArrowSpace: Boolean = false
    ): RemoteViews {
        tk.glucodata.Log.i("WidgetSettingsTrace", "header main=${config.glucoseScale} detail=${config.detailScale} weather=${config.weatherScale} bitmap=${WidgetClockRefresh.usesBitmapClock(context)} width=$w height=$h pxPerDp=$pxPerDp")
        val renderer = WidgetRenderer(context)
        val bitmap = renderer.renderClockHeader(config, snapshot, w, h, pxPerDp,
            weather, includeBackground, valueText, reserveTrendArrowSpace)
        return RemoteViews(context.packageName, R.layout.home_widget).apply {
            setImageViewBitmap(R.id.home_widget_image, bitmap)
            setContentDescription(R.id.home_widget_image, renderer.describe(snapshot))
        }
    }
}
