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
    RANGE(RangeGlucoseWidget::class.java, R.string.widget_range, R.string.widget_range_description, 320f, 150f),
    CLOCK_WEATHER(ClockWeatherWidget::class.java, R.string.widget_clock_weather, R.string.widget_clock_weather_description, 320f, 104f);

    val hasStats: Boolean get() = this == RANGE
}

enum class WidgetSettingsKind(
    val labelRes: Int,
    val descriptionRes: Int,
    val widgetKind: WidgetKind? = null,
    val hasGraph: Boolean = false,
    val hasStats: Boolean = false,
    val hasWeather: Boolean = false
) {
    BASIC(R.string.widget_classic, R.string.widget_classic_description),
    CHART(R.string.widget_expressive, R.string.widget_expressive_description, hasGraph = true, hasWeather = true),
    RANGE(R.string.widget_range, R.string.widget_range_description, WidgetKind.RANGE, hasStats = true),
    CLOCK_WEATHER(
        R.string.widget_clock_weather,
        R.string.widget_clock_weather_description,
        WidgetKind.CLOCK_WEATHER,
        hasWeather = true
    );

    companion object {
        fun from(kind: WidgetKind): WidgetSettingsKind =
            if (kind == WidgetKind.RANGE) RANGE else CLOCK_WEATHER
    }
}

enum class WidgetBackground { SURFACE, RANGE, TRANSPARENT }
enum class WidgetShape { SYSTEM, ROUND, PILL, SQUARE }
enum class WidgetStatsPeriod(val hours: Int) { TODAY(0), DAY(24), WEEK(168) }
enum class WidgetFontFamily { IBM_PLEX, SYSTEM }

data class WidgetConfig(
    val background: WidgetBackground = WidgetBackground.SURFACE,
    val opacity: Int = 92,
    val shape: WidgetShape = WidgetShape.SYSTEM,
    val textScale: Int = 100,
    val fontFamily: WidgetFontFamily = WidgetFontFamily.IBM_PLEX,
    val fontWeight: Int = 400,
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
        .put("fontFamily", fontFamily.name)
        .put("fontWeight", fontWeight)
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
                    fontFamily = enum(json.optString("fontFamily"), fallback.fontFamily),
                    fontWeight = json.optInt("fontWeight", fallback.fontWeight).coerceIn(300, 700),
                    rangeColors = json.optBoolean("rangeColors", fallback.rangeColors),
                    showArrow = json.optBoolean("showArrow", fallback.showArrow),
                    showDelta = json.optBoolean("showDelta", fallback.showDelta),
                    showTime = json.optBoolean("showTime", fallback.showTime),
                    showUnit = json.optBoolean("showUnit", fallback.showUnit),
                    graphHours = json.optInt("graphHours", fallback.graphHours)
                        .let { if (it in listOf(1,3,6,12,24)) it else fallback.graphHours },
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

private fun WidgetSettingsKind.baseDefault(): WidgetConfig = when (this) {
    WidgetSettingsKind.BASIC -> WidgetConfig(
        background = WidgetBackground.TRANSPARENT,
        rangeColors = false,
        showDelta = false,
        showTime = true,
        showUnit = false
    )
    WidgetSettingsKind.CHART -> WidgetConfig(
        background = WidgetBackground.SURFACE,
        opacity = 100,
        shape = WidgetShape.SYSTEM,
        showTime = false,
        graphHours = 6,
        showTargetBand = true
    )
    WidgetSettingsKind.RANGE -> WidgetConfig(statsPeriod = WidgetStatsPeriod.DAY)
    WidgetSettingsKind.CLOCK_WEATHER -> WidgetConfig(
        background = WidgetBackground.SURFACE,
        opacity = 65,
        shape = WidgetShape.ROUND,
        showTime = false
    )
}

object WidgetConfigStore {
    private const val PREFS = "home_widget_defaults"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun defaults(context: Context, kind: WidgetSettingsKind): WidgetConfig {
        val base = kind.baseDefault()
        if (kind != WidgetSettingsKind.BASIC && kind != WidgetSettingsKind.CHART) return base
        val legacy = context.applicationContext
            .getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        return base.copy(
            textScale = (legacy.getFloat("notification_font_size", 1.0f) * 100f).toInt().coerceIn(70, 140),
            fontFamily = if (legacy.getInt("notification_font_family", 0) == 1)
                WidgetFontFamily.SYSTEM else WidgetFontFamily.IBM_PLEX,
            fontWeight = legacy.getInt("notification_font_weight", 400).coerceIn(300, 700),
            showArrow = legacy.getBoolean("notification_show_arrow", true)
        )
    }

    fun load(context: Context, kind: WidgetSettingsKind): WidgetConfig =
        WidgetConfig.fromJson(prefs(context).getString(kind.name, null), defaults(context, kind))

    fun save(context: Context, kind: WidgetSettingsKind, config: WidgetConfig) {
        prefs(context).edit().putString(kind.name, config.toJson().toString()).apply()
    }

    fun load(context: Context, kind: WidgetKind): WidgetConfig =
        load(context, WidgetSettingsKind.from(kind))

    fun save(context: Context, kind: WidgetKind, config: WidgetConfig) =
        save(context, WidgetSettingsKind.from(kind), config)
}
