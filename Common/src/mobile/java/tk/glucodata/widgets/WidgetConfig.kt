package tk.glucodata.widgets

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    val hasGraph: Boolean get() = false
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
    val cornerRadiusDp: Int = 0,
    val textScale: Int = 100,
    val timeScale: Int = 100,
    val glucoseScale: Int = 100,
    val detailScale: Int = 100,
    val weatherScale: Int = 110,
    val weatherIconScale: Int = 100,
    val temperatureScale: Int = 100,
    val weatherShiftDp: Int = 12,
    val fontFamily: WidgetFontFamily = WidgetFontFamily.IBM_PLEX,
    val mainFontFamily: WidgetFontFamily = WidgetFontFamily.IBM_PLEX,
    val detailFontFamily: WidgetFontFamily = WidgetFontFamily.IBM_PLEX,
    val primaryFontWeight: Int = 400,
    val secondaryFontWeight: Int = 400,
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
        .put("cornerRadiusDp", cornerRadiusDp)
        .put("textScale", textScale)
        .put("timeScale", timeScale)
        .put("glucoseScale", glucoseScale)
        .put("detailScale", detailScale)
        .put("weatherScale", weatherScale)
        .put("weatherIconScale", weatherIconScale)
        .put("temperatureScale", temperatureScale)
        .put("weatherShiftDp", weatherShiftDp)
        .put("fontFamily", fontFamily.name)
        .put("mainFontFamily", mainFontFamily.name)
        .put("detailFontFamily", detailFontFamily.name)
        .put("primaryFontWeight", primaryFontWeight)
        .put("secondaryFontWeight", secondaryFontWeight)
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
                    cornerRadiusDp = json.optInt("cornerRadiusDp", fallback.cornerRadiusDp).coerceIn(0, 24),
                    textScale = json.optInt("textScale", fallback.textScale).coerceIn(70, 140),
                    timeScale = json.optInt("timeScale", fallback.timeScale).coerceIn(70, 160),
                    glucoseScale = json.optInt("glucoseScale", fallback.glucoseScale).coerceIn(70, 180),
                    detailScale = json.optInt("detailScale", fallback.detailScale).coerceIn(70, 180),
                    weatherScale = json.optInt("weatherScale", fallback.weatherScale).coerceIn(70, 200),
                    weatherIconScale = json.optInt("weatherIconScale", fallback.weatherIconScale).coerceIn(70, 220),
                    temperatureScale = json.optInt("temperatureScale", fallback.temperatureScale).coerceIn(70, 200),
                    weatherShiftDp = json.optInt("weatherShiftDp", fallback.weatherShiftDp).coerceIn(0, 40),
                    fontFamily = enum(json.optString("fontFamily"), fallback.fontFamily),
                    mainFontFamily = enum(json.optString("mainFontFamily"), enum(json.optString("fontFamily"), fallback.mainFontFamily)),
                    detailFontFamily = enum(json.optString("detailFontFamily"), enum(json.optString("fontFamily"), fallback.detailFontFamily)),
                    primaryFontWeight = json.optInt(
                        "primaryFontWeight",
                        json.optInt("fontWeight", fallback.primaryFontWeight)
                    ).coerceIn(300, 700),
                    secondaryFontWeight = json.optInt(
                        "secondaryFontWeight",
                        json.optInt("fontWeight", fallback.secondaryFontWeight)
                    ).coerceIn(300, 700),
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
        background = WidgetBackground.SURFACE,
        opacity = 65,
        rangeColors = false,
        showDelta = false,
        showTime = true,
        showUnit = false
    )
    WidgetSettingsKind.CHART -> WidgetConfig(
        background = WidgetBackground.SURFACE,
        opacity = 65,
        cornerRadiusDp = 0,
        showTime = false,
        graphHours = 6,
        showTargetBand = true
    )
    WidgetSettingsKind.RANGE -> WidgetConfig(
        background = WidgetBackground.SURFACE,
        opacity = 65,
        statsPeriod = WidgetStatsPeriod.DAY
    )
    WidgetSettingsKind.CLOCK_WEATHER -> WidgetConfig(
        background = WidgetBackground.SURFACE,
        opacity = 65,
        cornerRadiusDp = 0,
        showTime = false
    )
}

object WidgetConfigStore {
    private const val PREFS = "home_widget_defaults"
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun defaults(context: Context, kind: WidgetSettingsKind): WidgetConfig {
        val base = kind.baseDefault()
        val legacy = context.applicationContext
            .getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
        return base.copy(
            textScale = (legacy.getFloat("notification_font_size", 1.0f) * 100f).toInt().coerceIn(70, 140),
            fontFamily = if (legacy.getInt("notification_font_family", 0) == 1)
                WidgetFontFamily.SYSTEM else WidgetFontFamily.IBM_PLEX,
            mainFontFamily = if (legacy.getInt("notification_font_family", 0) == 1) WidgetFontFamily.SYSTEM else WidgetFontFamily.IBM_PLEX,
            detailFontFamily = if (legacy.getInt("notification_font_family", 0) == 1) WidgetFontFamily.SYSTEM else WidgetFontFamily.IBM_PLEX,
            primaryFontWeight = legacy.getInt("notification_font_weight", 400).coerceIn(300, 700),
            secondaryFontWeight = legacy.getInt("notification_font_weight", 400).coerceIn(300, 700),
            showArrow = legacy.getBoolean("notification_show_arrow", true)
        )
    }

    // One-time migration: align even existing widget configurations to 65% opacity.
    // Afterwards the opacity sliders stay independent and fully editable.
    private fun migrateOpacityTo65(context: Context) {
        val preferences = prefs(context)
        if (preferences.getBoolean("opacity65_migrated", false)) return
        synchronized(this) {
            if (preferences.getBoolean("opacity65_migrated", false)) return
            val editor = preferences.edit()
            WidgetSettingsKind.entries.forEach { kind ->
                val raw = preferences.getString(kind.name, null)
                if (raw != null) {
                    val config = WidgetConfig.fromJson(raw, defaults(context, kind))
                    editor.putString(kind.name, config.copy(opacity = 65).toJson().toString())
                }
            }
            editor.putBoolean("opacity65_migrated", true).commit()
        }
    }

    fun load(context: Context, kind: WidgetSettingsKind): WidgetConfig {
        migrateOpacityTo65(context)
        val saved = WidgetConfig.fromJson(prefs(context).getString(kind.name, null), defaults(context, kind))
        return saved.copy(
            mainFontFamily = saved.fontFamily,
            detailFontFamily = saved.fontFamily,
            secondaryFontWeight = saved.primaryFontWeight
        )
    }

    fun save(context: Context, kind: WidgetSettingsKind, config: WidgetConfig) {
        prefs(context).edit().putString(kind.name, config.toJson().toString()).apply()
        // Active Glance sessions must reload settings even without a glucose tick.
        synchronized(mutableRevision) {
            mutableRevision.value = mutableRevision.value + 1L
        }
    }

    fun load(context: Context, kind: WidgetKind): WidgetConfig =
        load(context, WidgetSettingsKind.from(kind))

    fun save(context: Context, kind: WidgetKind, config: WidgetConfig) =
        save(context, WidgetSettingsKind.from(kind), config)
}
