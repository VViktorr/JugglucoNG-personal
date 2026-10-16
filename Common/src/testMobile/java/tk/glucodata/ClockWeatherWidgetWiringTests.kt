package tk.glucodata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockWeatherWidgetWiringTests {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { if (File(it, "src/mobile").isDirectory) it else File(it, "Common") }
        .first { File(it, "src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt").isFile }

    private fun mobile(path: String) = File(root, "src/mobile/$path").readText()

    @Test
    fun clockUsesNativeTextClockAndFullDate() {
        val layout = mobile("res/layout/home_widget_clock_weather.xml")
        val updater = mobile("java/tk/glucodata/widgets/WidgetUpdater.kt")
        assertTrue(layout.contains("<TextClock"))
        assertTrue(layout.contains("home_widget_clock"))
        assertTrue(layout.contains("home_widget_date"))
        assertTrue(layout.contains("EEEE, d MMMM"))
        assertTrue(updater.contains("R.layout.home_widget_clock_weather"))
        assertTrue(updater.contains("config.glucoseScale"))
    }

    @Test
    fun weatherIsNearClockAndGlucoseIsOnRight() {
        val renderer = mobile("java/tk/glucodata/widgets/WidgetRenderer.kt")
        assertTrue(renderer.contains("weatherCenterX = (w * 0.44f - c.weatherShiftDp * density)"))
        assertTrue(renderer.contains("glucoseCenterX = w * 0.80f"))
        val start = renderer.indexOf("private fun drawClockWeather")
        val end = renderer.indexOf("private fun drawRange", start)
        assertTrue(start >= 0 && end > start)
        val body = renderer.substring(start, end)
        assertTrue(!body.contains("SimpleDateFormat"))
        assertTrue(!body.contains("timeText"))
    }

    @Test
    fun clockGlucoseAndWeatherSizingControlsAreAvailable() {
        val config = mobile("java/tk/glucodata/widgets/WidgetConfig.kt")
        val screen = mobile("java/tk/glucodata/widgets/WidgetSettingsScreen.kt")
        assertTrue(config.contains("timeScale"))
        assertTrue(config.contains("glucoseScale"))
        assertTrue(config.contains("weatherScale"))
        assertTrue(config.contains("weatherShiftDp"))
        assertTrue(screen.contains("widget_clock_glucose_size_value"))
        assertTrue(screen.contains("widget_clock_detail_size_value"))
        assertTrue(screen.contains("widget_glucose_size_value"))
        assertTrue(screen.contains("widget_weather_size_value"))
        assertTrue(screen.contains("widget_weather_shift_value"))
    }
}
