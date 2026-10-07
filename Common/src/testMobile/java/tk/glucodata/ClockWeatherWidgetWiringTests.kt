package tk.glucodata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockWeatherWidgetWiringTests {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { if (File(it, "src/mobile").isDirectory) it else File(it, "Common") }
        .first { File(it, "src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt").isFile }

    private fun mobile(path: String) = File(root, "src/mobile/$path").readText()

    @Test fun clockWeatherIsRegisteredAlongsideExistingWidgetVariants() {
        val manifest = mobile("AndroidManifest.xml")
        val kind = mobile("java/tk/glucodata/widgets/WidgetConfig.kt")
        assertTrue(manifest.contains(".widgets.ClockWeatherWidget"))
        assertTrue(manifest.contains("home_widget_clock_weather_info"))
        assertTrue(kind.contains("CLOCK_WEATHER(ClockWeatherWidget::class.java"))
        assertTrue(manifest.contains(".GlucoseWidget"))
        assertTrue(manifest.contains(".widget.ExpressiveWidgetReceiver"))
    }

    @Test fun clockUsesNativeMinuteUpdatingTextClock() {
        val layout = mobile("res/layout/home_widget_clock_weather.xml")
        val updater = mobile("java/tk/glucodata/widgets/WidgetUpdater.kt")
        assertTrue(layout.contains("<TextClock"))
        assertTrue(layout.contains("home_widget_clock"))
        assertTrue(layout.contains("home_widget_date"))
        assertTrue(layout.contains("EEE, d MMMM"))
        assertTrue(layout.contains("format24Hour"))
        assertTrue(updater.contains("R.layout.home_widget_clock_weather"))
        assertTrue(updater.contains("setTextViewTextSize(R.id.home_widget_clock"))
        assertTrue(updater.contains("setTextViewTextSize(R.id.home_widget_date"))
    }

    @Test fun glucoseStatesAndWeatherStayIndependent() {
        val renderer = mobile("java/tk/glucodata/widgets/WidgetRenderer.kt")
        val data = mobile("java/tk/glucodata/widgets/WidgetData.kt")
        val weather = mobile("java/tk/glucodata/widgets/WidgetWeatherSource.kt")
        assertTrue(renderer.contains("drawClockWeather"))
        assertTrue(renderer.contains("glucoseCenterX = w * 0.60f"))
        assertTrue(renderer.contains("weatherX = w * 0.885f"))
        assertTrue(!renderer.substring(renderer.indexOf("private fun drawClockWeather"), renderer.indexOf("private fun drawDial")).contains("SimpleDateFormat"))
        assertTrue(renderer.contains("s.statusText"))
        assertTrue(data.contains("WidgetDisplaySource.resolveChartHistory"))
        assertTrue(weather.contains("ACCESS_COARSE_LOCATION"))
        assertTrue(weather.contains("api.open-meteo.com"))
        assertTrue(weather.contains("MAX_AGE_MS"))
    }

    @Test fun weatherControlsLiveInsideDisplayWidgetSettings() {
        val screen = mobile("java/tk/glucodata/widgets/WidgetSettingsScreen.kt")
        assertTrue(screen.contains("WidgetKind.entries"))
        assertTrue(screen.contains("R.string.widget_show_date"))
        assertTrue(screen.contains("R.string.widget_show_weather_icon"))
        assertTrue(screen.contains("R.string.widget_show_temperature"))
        assertTrue(screen.contains("ActivityResultContracts.RequestPermission()"))
    }
}
