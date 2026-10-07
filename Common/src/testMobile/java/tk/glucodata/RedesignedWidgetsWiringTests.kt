package tk.glucodata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RedesignedWidgetsWiringTests {
    private val root: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            val common = if (File(cursor, "src/mobile").isDirectory) cursor else File(cursor, "Common")
            if (File(common, "src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found")
    }

    @Test
    fun fiveAdditionalWidgetsStayAlongsideExistingTwo() {
        val manifest = File(root, "src/mobile/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".GlucoseWidget"))
        assertTrue(manifest.contains(".widget.ExpressiveWidgetReceiver"))
        assertTrue(manifest.contains(".widgets.MinimalGlucoseWidget"))
        assertTrue(manifest.contains(".widgets.CompactGlucoseWidget"))
        assertTrue(manifest.contains(".widgets.TrendGlucoseWidget"))
        assertTrue(manifest.contains(".widgets.DialGlucoseWidget"))
        assertTrue(manifest.contains(".widgets.RangeGlucoseWidget"))
    }

    @Test
    fun redesignedWidgetsUseSharedDisplayAndAppChartPipeline() {
        val data = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetData.kt").readText()
        val renderer = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetRenderer.kt").readText()
        assertTrue(data.contains("WidgetDisplaySource.resolveWidgetSnapshot"))
        assertTrue(data.contains("WidgetDisplaySource.resolveChartHistory"))
        assertTrue(data.contains("WidgetDisplaySource.resolveDisplaySnapshot"))
        assertTrue(renderer.contains("NotificationChartDrawer.drawChart"))
    }

    @Test
    fun widgetStatusIncludesStaleWarmupAndFinishedStates() {
        val sharedStatus = File(root, "src/mobile/java/tk/glucodata/WidgetStatusText.kt").readText()
        val data = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetData.kt").readText()
        val classic = File(root, "src/main/java/tk/glucodata/GlucoseWidget.java").readText()
        assertTrue(sharedStatus.contains("R.string.nonewvalue"))
        assertTrue(sharedStatus.contains("R.string.status_ready_in_minutes"))
        assertTrue(sharedStatus.contains("R.string.widget_sensor_finished"))
        assertTrue(sharedStatus.contains("Natives.getSensorWarmupMinutes"))
        assertTrue(data.contains("WidgetStatusText.resolve"))
        assertTrue(!classic.contains("WidgetStatusText.resolve"))
    }

    @Test
    fun nfcFullMergeRefreshPathFeedsWidgetsAfterHistorySync() {
        val historySync = File(root, "src/mobile/java/tk/glucodata/data/HistorySync.kt").readText()
        val mergeStart = historySync.indexOf("fun mergeFullSyncForSensor(serial: String)")
        val mergeEnd = historySync.indexOf("private fun scheduleDelayedFullMerge", mergeStart)
        assertTrue(mergeStart >= 0 && mergeEnd > mergeStart)
        val body = historySync.substring(mergeStart, mergeEnd)
        assertTrue(body.indexOf("doSyncSensor(canonicalSerial, forceFull = true)") >= 0)
        assertTrue(body.indexOf("GlucoseUpdateBroadcaster.send()") > body.indexOf("doSyncSensor(canonicalSerial, forceFull = true)"))
    }

    @Test
    fun displaySettingsExposeCentralWidgetScreen() {
        val display = File(root, "src/mobile/java/tk/glucodata/ui/DisplaySettingsScreens.kt").readText()
        val nav = File(root, "src/mobile/java/tk/glucodata/ui/MainNavigation.kt").readText()
        val settings = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetSettingsScreen.kt").readText()
        assertTrue(!display.contains("navController.navigate(\"settings/widgets\")"))
        assertTrue(nav.split("composable(\"settings/widgets\")").size - 1 >= 2)
        val mainSettings = File(root, "src/mobile/java/tk/glucodata/ui/ExpressiveSettingsScreen.kt").readText()
        assertTrue(mainSettings.contains("navController.navigate(\"settings/widgets\")"))
        assertTrue(settings.contains("R.string.widget_classic"))
        assertTrue(settings.contains("R.string.widget_expressive"))
        assertTrue(settings.contains("WidgetKind.entries"))
    }

    @Test
    fun widgetsCoalesceGlucoseRefreshAndLeaveClockTicksToTextClock() {
        val providers = File(root, "src/mobile/java/tk/glucodata/widgets/GlucoseWidgets.kt").readText()
        val updater = File(root, "src/mobile/java/tk/glucodata/widgets/WidgetUpdater.kt").readText()
        assertTrue(providers.contains("GlucoseUpdateBroadcaster.ACTION_GLUCOSE_UPDATE"))
        assertTrue(providers.contains("WidgetUpdater.requestUpdateAll(context)"))
        assertTrue(!updater.contains("scheduleMinuteRefresh"))
        assertTrue(updater.contains("WidgetWeatherSource.cached"))
        assertTrue(updater.contains("scheduleWeatherRefresh"))
    }
}
