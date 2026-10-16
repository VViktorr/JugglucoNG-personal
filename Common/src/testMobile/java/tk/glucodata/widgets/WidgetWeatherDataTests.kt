package tk.glucodata.widgets

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WidgetWeatherDataTests {
    private val now = Instant.parse("2026-10-16T10:30:00Z").toEpochMilli()

    private fun station(
        name: String = "Warszawa",
        latitude: String = "52.23",
        longitude: String = "21.01",
        temperature: Any = "7.2",
        measured: String = "2026-10-16 10:20:00"
    ) = JSONObject().put("nazwa_stacji", name).put("lat", latitude).put("lon", longitude)
        .put("temperatura_powietrza", temperature).put("temperatura_powietrza_data", measured)

    @Test fun openMeteoKeepsConditionsAndRoundsNegativeTemperature() {
        val reading = requireNotNull(WidgetWeatherParser.openMeteo(
            JSONObject("""{"current":{"temperature_2m":-2.8,"weather_code":71}}"""), now
        ))
        assertEquals(-3, reading.temperatureC)
        assertEquals("❄", reading.symbol)
        assertEquals(now, reading.observedAt)
    }

    @Test fun openMeteoDoesNotTurnMissingTemperatureIntoZero() {
        assertNull(WidgetWeatherParser.openMeteo(JSONObject("""{"current":{"weather_code":0}}"""), now))
        assertNull(WidgetWeatherParser.openMeteo(JSONObject("""{"error":true}"""), now))
        assertNull(WidgetWeatherParser.openMeteo(JSONObject("""{"current":{"temperature_2m":999}}"""), now))
    }

    @Test fun imgwChoosesNearestStationWithUsableTemperature() {
        val rows = JSONArray().put(station("Further", "52.50"))
            .put(station("Missing", temperature = JSONObject.NULL))
            .put(station("Nearest", "52.24", temperature = "-3.6"))
        val reading = requireNotNull(WidgetWeatherParser.imgw(rows, 52.23, 21.01, now))
        assertEquals("Nearest", reading.stationName)
        assertEquals(-4, reading.temperatureC)
    }

    @Test fun imgwSkipsExpiredFutureAndMalformedMeasurements() {
        val rows = JSONArray().put(station("Expired", measured = "2026-10-16 07:00:00"))
            .put(station("Future", measured = "2026-10-16 11:00:00"))
            .put(station("Invalid", measured = "2026-02-30 10:20:00"))
            .put(station("Fresh", "52.24"))
        assertEquals("Fresh", WidgetWeatherParser.imgw(rows, 52.23, 21.01, now)?.stationName)
    }

    @Test fun imgwKeepsActualUtcTimeAndDoesNotInventAConditionIcon() {
        val reading = requireNotNull(WidgetWeatherParser.imgw(JSONArray().put(station()), 52.23, 21.01, now))
        assertEquals(Instant.parse("2026-10-16T10:20:00Z").toEpochMilli(), reading.observedAt)
        assertEquals(-1, reading.weatherCode)
        assertNull(reading.symbol)
    }

    @Test fun imgwDoesNotUsePolishWeatherForDistantOrInvalidLocations() {
        val rows = JSONArray().put(station())
        assertNull(WidgetWeatherParser.imgw(rows, 40.71, -74.0, now))
        assertNull(WidgetWeatherParser.imgw(rows, Double.NaN, 21.01, now))
        assertNull(WidgetWeatherParser.imgw(rows, 100.0, 21.01, now))
        assertNull(WidgetWeatherParser.imgw(JSONArray(), 52.23, 21.01, now))
    }

    @Test fun imgwIgnoresBadStationCoordinatesAndTemperatures() {
        val rows = JSONArray().put(station(latitude = "NaN"))
            .put(station(temperature = "not a number"))
            .put(station(temperature = "999"))
        assertNull(WidgetWeatherParser.imgw(rows, 52.23, 21.01, now))
    }

    @Test fun sourceSelectionSurvivesSavingWithoutChangingAppearance() {
        val config = WidgetConfig(weatherProvider = WidgetWeatherProvider.IMGW, detailScale = 135, showWeatherIcon = false)
        assertEquals(config, WidgetConfig.fromJson(config.toJson().toString(), WidgetConfig()))
    }

    @Test fun existingAndUnknownSourcePreferencesKeepOpenMeteo() {
        assertEquals(WidgetWeatherProvider.OPEN_METEO, WidgetConfig.fromJson("{}", WidgetConfig()).weatherProvider)
        assertEquals(WidgetWeatherProvider.OPEN_METEO,
            WidgetConfig.fromJson("""{"weatherProvider":"UNKNOWN"}""", WidgetConfig()).weatherProvider)
    }
}
