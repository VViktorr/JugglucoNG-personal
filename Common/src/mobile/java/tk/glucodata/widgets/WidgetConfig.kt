package tk.glucodata.widgets

import android.content.Context
import org.json.JSONObject
import tk.glucodata.R

enum class WidgetKind(
    val provider: Class<out BaseGlucoseWidget>,
    val labelRes: Int,
    val descriptionRes: Int,
    val defaultWidthDp: Float,
    val defaultHeightDp: Float
) {
    MINIMAL(MinimalGlucoseWidget::class.java, R.string.widget_minimal, R.string.widget_minimal_description, 150f, 80f),
    COMPACT(CompactGlucoseWidget::class.java, R.string.widget_compact, R.string.widget_compact_description, 200f, 72f),
    TREND(TrendGlucoseWidget::class.java, R.string.widget_trend, R.string.widget_trend_description, 320f, 160f),
    DIAL(DialGlucoseWidget::class.java, R.string.widget_dial, R.string.widget_dial_description, 160f, 160f),
    RANGE(RangeGlucoseWidget::class.java, R.string.widget_range, R.string.widget_range_description, 320f, 150f),
    CLOCK_WEATHER(ClockWeatherWidget::class.java, R.string.widget_clock_weather, R.string.widget_clock_weather_description, 320f, 104f);

    val hasGraph: Boolean get() = this == TREND
    val hasStats: Boolean get() = this == RANGE
}

enum class WidgetBackground { SURFACE, RANGE, TRANSPARENT }
enum class WidgetShape { SYSTEM, ROUND, PILL, SQUARE }
enum class WidgetStatsPeriod(val hours: Int) { TODAY(0), DAY(24), WEEK(168) }

data class WidgetConfig(
    val background: WidgetBackground = WidgetBackground.SURFACE,
    val opacity: Int = 92,
    val shape: WidgetShape = WidgetShape.SYSTEM,
    val textScale: Int = 100,
    val rangeColors: Boolean = true,
    val showArrow: Boolean = true,
    val showDelta: Boolean = true,
    val showTime: Boolean = true,
    val showUnit: Boolean = false,
    val graphHours: Int = 6,
    val showTargetBand: Boolean = true,
    val statsPeriod: WidgetStatsPeriod = WidgetStatsPeriod.DAY,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val showWeatherIcon: Boolean = true,
    val showTemperature: Boolean = true
) {
    fun toJson() = JSONObject()
        .put("background", background.name)
        .put("opacity", opacity)
        .put("shape", shape.name)
        .put("textScale", textScale)
        .put("rangeColors", rangeColors)
        .put("showArrow", showArrow)
        .put("showDelta", showDelta)
        .put("showTime", showTime)
        .put("showUnit", showUnit)
        .put("graphHours", graphHours)
        .put("showTargetBand", showTargetBand)
        .put("statsPeriod", statsPeriod.name)
        .put("showDate", showDate)
        .put("showWeather", showWeather)
        .put("showWeatherIcon", showWeatherIcon)
        .put("showTemperature", showTemperature)

    companion object {
        private inline fun <reified T : Enum<T>> enum(raw: String?, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == raw } ?: fallback

        fun fromJson(raw: String?, fallback: WidgetConfig): WidgetConfig {
            if (raw.isNullOrBlank()) return fallback
            return try {
                val json = JSONObject(raw)
                WidgetConfig(
                    background = enum(json.optString("background"), fallback.background),
                    opacity = json.optInt("opacity", fallback.opacity).coerceIn(0, 100),
                    shape = enum(json.optString("shape"), fallback.shape),
                    textScale = json.optInt("textScale", fallback.textScale).coerceIn(70, 140),
                    rangeColors = json.optBoolean("rangeColors", fallback.rangeColors),
                    showArrow = json.optBoolean("showArrow", fallback.showArrow),
                    showDelta = json.optBoolean("showDelta", fallback.showDelta),
                    showTime = json.optBoolean("showTime", fallback.showTime),
                    showUnit = json.optBoolean("showUnit", fallback.showUnit),
                    graphHours = json.optInt("graphHours", fallback.graphHours).let { if (it in listOf(1,3,6,12,24)) it else fallback.graphHours },
                    showTargetBand = json.optBoolean("showTargetBand", fallback.showTargetBand),
                    statsPeriod = enum(json.optString("statsPeriod"), fallback.statsPeriod),
                    showDate = json.optBoolean("showDate", fallback.showDate),
                    showWeather = json.optBoolean("showWeather", fallback.showWeather),
                    showWeatherIcon = json.optBoolean("showWeatherIcon", fallback.showWeatherIcon),
                    showTemperature = json.optBoolean("showTemperature", fallback.showTemperature)
                )
            } catch (_: Throwable) {
                fallback
            }
        }
    }
}

fun WidgetKind.defaultConfig(): WidgetConfig = when (this) {
    WidgetKind.MINIMAL -> WidgetConfig(background = WidgetBackground.TRANSPARENT, showDelta = false, showUnit = false)
    WidgetKind.COMPACT -> WidgetConfig(shape = WidgetShape.PILL)
    WidgetKind.TREND -> WidgetConfig(graphHours = 6)
    WidgetKind.DIAL -> WidgetConfig(shape = WidgetShape.ROUND, showTime = true)
    WidgetKind.RANGE -> WidgetConfig(statsPeriod = WidgetStatsPeriod.DAY)
    WidgetKind.CLOCK_WEATHER -> WidgetConfig(background = WidgetBackground.SURFACE, opacity = 65, shape = WidgetShape.ROUND, showTime = false)
}

object WidgetConfigStore {
    private const val PREFS = "home_widget_defaults"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, kind: WidgetKind): WidgetConfig =
        WidgetConfig.fromJson(prefs(context).getString(kind.name, null), kind.defaultConfig())

    fun save(context: Context, kind: WidgetKind, config: WidgetConfig) {
        prefs(context).edit().putString(kind.name, config.toJson().toString()).apply()
    }
}
