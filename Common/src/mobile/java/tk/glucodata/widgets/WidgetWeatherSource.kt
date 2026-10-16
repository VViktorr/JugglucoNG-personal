package tk.glucodata.widgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.net.HttpURLConnection
import java.net.URL

/** Weather from the selected public provider, using granted approximate location.
 * Each provider has its own cache; switching sources never displays the other feed.
 */
object WidgetWeatherSource {
    private const val CACHE = "clock_weather_cache"
    private const val MAX_AGE_MS = 45 * 60_000L
    private const val LOCATION_MAX_AGE_MS = 24 * 60 * 60_000L

    private fun preferences(context: Context, provider: WidgetWeatherProvider) =
        context.getSharedPreferences(
            if (provider == WidgetWeatherProvider.OPEN_METEO) CACHE else "${CACHE}_${provider.name}",
            Context.MODE_PRIVATE
        )

    fun cached(context: Context, provider: WidgetWeatherProvider = WidgetWeatherProvider.OPEN_METEO): WidgetWeather? {
        val prefs = preferences(context, provider)
        val now = System.currentTimeMillis()
        val savedAt = prefs.getLong("observed", 0L)
        if (savedAt <= 0L || savedAt > now + 5 * 60_000L ||
            now - savedAt >= WidgetWeatherParser.MAX_OBSERVATION_AGE_MS) return null
        return WidgetWeather(
            prefs.getInt("temperature", 0), prefs.getInt("code", -1), savedAt,
            prefs.getString("station", null)
        )
    }

    fun needsRefresh(context: Context, provider: WidgetWeatherProvider = WidgetWeatherProvider.OPEN_METEO): Boolean {
        val prefs = preferences(context, provider)
        val now = System.currentTimeMillis()
        val fetchedAt = prefs.getLong("fetched", prefs.getLong("observed", 0L))
        if (cached(context, provider) != null && fetchedAt > 0L && fetchedAt <= now &&
            now - fetchedAt < MAX_AGE_MS) return false
        val lastAttempt = prefs.getLong("last_attempt", 0L)
        return lastAttempt <= 0L || lastAttempt > now || now - lastAttempt >= 10 * 60_000L
    }

    fun load(context: Context, provider: WidgetWeatherProvider = WidgetWeatherProvider.OPEN_METEO): WidgetWeather? {
        val prefs = preferences(context, provider)
        val previous = cached(context, provider)
        if (!needsRefresh(context, provider)) return previous
        val now = System.currentTimeMillis()
        val location = lastLocation(context) ?: return previous
        // A denied permission must not delay the first update after the user grants it.
        prefs.edit().putLong("last_attempt", now).apply()
        return try {
            val url = when (provider) {
                WidgetWeatherProvider.OPEN_METEO ->
                    "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                        "&longitude=${location.longitude}&current=temperature_2m,weather_code&forecast_days=1"
                WidgetWeatherProvider.IMGW -> "https://danepubliczne.imgw.pl/api/data/meteo/"
            }
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout = if (provider == WidgetWeatherProvider.IMGW) 10_000 else 4_000
            try {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val reading = when (provider) {
                    WidgetWeatherProvider.OPEN_METEO -> WidgetWeatherParser.openMeteo(JSONObject(response), now)
                    WidgetWeatherProvider.IMGW -> WidgetWeatherParser.imgw(
                        JSONArray(response), location.latitude, location.longitude, now
                    )
                } ?: return previous
                prefs.edit().putLong("observed", reading.observedAt).putLong("fetched", now)
                    .putInt("temperature", reading.temperatureC).putInt("code", reading.weatherCode)
                    .putString("station", reading.stationName).apply()
                reading
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            previous
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
