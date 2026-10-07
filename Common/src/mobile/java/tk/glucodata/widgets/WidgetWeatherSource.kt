package tk.glucodata.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL

data class WidgetWeather(val temperatureC: Int, val weatherCode: Int, val observedAt: Long)

/**
 * Android has no standard public weather-provider API. Fallback is Open-Meteo,
 * with no API key, based on last known location only (never asks for permission).
 * A cached reading avoids network activity during minute-by-minute clock ticks.
 */
object WidgetWeatherSource {
    private const val CACHE = "clock_weather_cache"
    private const val MAX_AGE_MS = 45 * 60_000L
    private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60_000L

    fun cached(context: Context): WidgetWeather? {
        val prefs = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val savedAt = prefs.getLong("observed", 0L)
        if (savedAt <= 0L || savedAt > now || now - savedAt >= 4 * MAX_AGE_MS) return null
        return WidgetWeather(
            prefs.getInt("temperature", 0),
            prefs.getInt("code", -1),
            savedAt
        )
    }

    fun needsRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val savedAt = prefs.getLong("observed", 0L)
        if (savedAt > 0L && savedAt <= now && now - savedAt < MAX_AGE_MS) return false
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        return lastAttempt <= 0L || lastAttempt > now || now - lastAttempt >= 10 * 60_000L
    }

    fun load(context: Context): WidgetWeather? {
        val prefs = context.getSharedPreferences(CACHE, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        val savedAt = prefs.getLong("observed", 0L)
        val cached = if (savedAt > 0 && savedAt <= now)
            WidgetWeather(prefs.getInt("temperature", 0), prefs.getInt("code", -1), savedAt)
        else null
        if (cached != null && now - savedAt < MAX_AGE_MS) return cached
        if (lastAttempt in 1..now && now - lastAttempt < 10 * 60_000L)
            return cached?.takeIf { now - it.observedAt < 4 * MAX_AGE_MS }

        // A standard system weather API does not exist. Avoid trying private
        // provider URIs that may expose unrelated personal information.
        val location = lastLocation(context) ?: return cached?.takeIf { now - it.observedAt < 4 * MAX_AGE_MS }
        prefs.edit().putLong("last_attempt", now).apply()
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                    "&longitude=${location.longitude}&current=temperature_2m,weather_code&forecast_days=1"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            try {
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val current = json.getJSONObject("current")
                val reading = WidgetWeather(
                    current.getDouble("temperature_2m").toInt(),
                    current.getInt("weather_code"),
                    now
                )
                prefs.edit().putLong("observed", now)
                    .putInt("temperature", reading.temperatureC)
                    .putInt("code", reading.weatherCode).apply()
                reading
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            cached?.takeIf { now - it.observedAt < 4 * MAX_AGE_MS }
        }
    }

    @Suppress("MissingPermission")
    private fun lastLocation(context: Context): Location? {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val now = System.currentTimeMillis()
        val providers = manager.getProviders(true)
        val cached = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { it.time > 0 && now - it.time in 0..LOCATION_MAX_AGE_MS }
            .maxByOrNull { it.time }
        if (cached != null) return cached

        // Some devices do not keep a usable last-known location. The widget
        // refresh already runs on a background executor, so on Android 11+
        // request one approximate location and wait briefly for the callback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val orderedProviders = providers.sortedBy {
            when (it) {
                LocationManager.NETWORK_PROVIDER -> 0
                LocationManager.GPS_PROVIDER -> 1
                else -> 2
            }
        }
        for (provider in orderedProviders) {
            var resolved: Location? = null
            val latch = CountDownLatch(1)
            try {
                manager.getCurrentLocation(
                    provider,
                    null,
                    java.util.concurrent.Executor { command -> command.run() }
                ) { location ->
                    resolved = location
                    latch.countDown()
                }
                latch.await(2500L, TimeUnit.MILLISECONDS)
            } catch (_: Throwable) {
                latch.countDown()
            }
            resolved?.let { return it }
        }
        return null
    }
}
