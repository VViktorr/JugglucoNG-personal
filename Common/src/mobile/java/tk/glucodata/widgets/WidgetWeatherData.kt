package tk.glucodata.widgets

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

enum class WidgetWeatherProvider { OPEN_METEO, IMGW }

data class WidgetWeather(
    val temperatureC: Int,
    val weatherCode: Int,
    val observedAt: Long,
    val stationName: String? = null
) {
    val symbol: String? get() = when (weatherCode) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        in 45..48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, in 85..86 -> "❄"
        in 95..99 -> "ϟ"
        else -> null
    }
}

internal object WidgetWeatherParser {
    const val MAX_OBSERVATION_AGE_MS = 3 * 60 * 60_000L
    private const val MAX_STATION_DISTANCE_KM = 150.0
    private val imgwTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun openMeteo(json: JSONObject, now: Long): WidgetWeather? {
        val current = json.optJSONObject("current") ?: return null
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        if (!temperature.isFinite() || temperature !in -100.0..80.0) return null
        return WidgetWeather(temperature.roundToInt(), current.optInt("weather_code", -1), now)
    }

    /** Public IMGW meteorological measurements include coordinates and UTC timestamps. */
    fun imgw(rows: JSONArray, latitude: Double, longitude: Double, now: Long): WidgetWeather? {
        if (!validCoordinates(latitude, longitude)) return null
        var nearest: WidgetWeather? = null
        var nearestKm = MAX_STATION_DISTANCE_KM
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val lat = row.optDouble("lat", Double.NaN)
            val lon = row.optDouble("lon", Double.NaN)
            val temperature = row.optDouble("temperatura_powietrza", Double.NaN)
            if (!validCoordinates(lat, lon) || !temperature.isFinite() || temperature !in -100.0..80.0) continue
            val measuredAt = runCatching {
                LocalDateTime.parse(row.optString("temperatura_powietrza_data"), imgwTime)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull() ?: continue
            if (measuredAt <= 0L || measuredAt > now + 5 * 60_000L ||
                now - measuredAt >= MAX_OBSERVATION_AGE_MS) continue
            val km = distanceKm(latitude, longitude, lat, lon)
            if (km < nearestKm || (km == nearestKm && measuredAt > (nearest?.observedAt ?: 0L))) {
                nearestKm = km
                // This feed has no cloud/condition code; do not invent a weather icon.
                nearest = WidgetWeather(
                    temperature.roundToInt(), -1, measuredAt,
                    row.optString("nazwa_stacji").takeIf { it.isNotBlank() }
                )
            }
        }
        return nearest
    }

    private fun validCoordinates(latitude: Double, longitude: Double) =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
