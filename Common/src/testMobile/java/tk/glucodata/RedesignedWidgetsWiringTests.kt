package tk.glucodata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RedesignedWidgetsWiringTests {
    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .map { if (File(it, "src/mobile").isDirectory) it else File(it, "Common") }
        .first { File(it, "src/mobile/AndroidManifest.xml").isFile }

    @Test
    fun exactlyFourHomeScreenWidgetsAreRegistered() {
        val manifest = File(root, "src/mobile/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".GlucoseWidget"))
        assertTrue(manifest.contains(".widget.ExpressiveWidgetReceiver"))
        assertTrue(manifest.contains(".widgets.RangeGlucoseWidget"))
        assertTrue(manifest.contains(".widgets.ClockWeatherWidget"))
        assertTrue(!manifest.contains(".widgets.MinimalGlucoseWidget"))
        assertTrue(!manifest.contains(".widgets.CompactGlucoseWidget"))
        assertTrue(!manifest.contains(".widgets.TrendGlucoseWidget"))
        assertTrue(!manifest.contains(".widgets.DialGlucoseWidget"))
    }

    @Test
    fun centralSettingsExposeFourWidgetKinds() {
        val config = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetConfig.kt").readText()
        assertTrue(config.contains("BASIC(R.string.widget_classic"))
        assertTrue(config.contains("CHART(R.string.widget_expressive"))
        assertTrue(config.contains("RANGE(R.string.widget_range"))
        assertTrue(config.contains("CLOCK_WEATHER("))
    }

    @Test
    fun updateCoalescingDoesNotDropLatestReading() {
        val updater = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetUpdater.kt").readText()
        assertTrue(updater.contains("rerunRequested"))
        assertTrue(updater.contains("if(rerun) requestUpdateAll(app)"))
        assertTrue(updater.contains("600_000f"))
    }
}
