package tk.glucodata.widget

import tk.glucodata.widgets.WidgetConfig

/** Reserve real header space for the configured fonts before allocating the chart. */
internal object ChartWidgetSizing {
    const val GAP_DP = 2f

    fun headerHeightDp(heightDp: Float, config: WidgetConfig, fontScale: Float): Float {
        val weatherScale = if (config.showWeather) {
            val icon = if (config.showWeatherIcon) config.weatherIconScale / 100f else 0f
            val temperature = if (config.showTemperature) config.temperatureScale / 100f else 0f
            config.weatherScale / 100f * maxOf(icon, temperature)
        } else 0f
        val scale = maxOf(1f, config.glucoseScale / 100f,
            config.detailScale / 100f, weatherScale)
        val desired = 104f * scale * fontScale.coerceAtLeast(1f)
        // Preserve the existing 144dp chart threshold on short launcher cells.
        // Taller cells let the header grow with the user's chosen sizes.
        val available = if (heightDp >= 144f) heightDp - GAP_DP - 60f else heightDp
        return desired.coerceAtMost(available.coerceAtLeast(1f))
    }

    fun chartHeightDp(heightDp: Float, headerHeightDp: Float): Float =
        (heightDp - headerHeightDp - GAP_DP).coerceAtLeast(0f)
}
