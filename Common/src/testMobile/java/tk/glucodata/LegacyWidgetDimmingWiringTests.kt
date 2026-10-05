package tk.glucodata

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWidgetDimmingWiringTests {
    private val moduleRoot: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            if (File(cursor, "src/main/java/tk/glucodata/RemoteGlucose.java").isFile) {
                found = cursor
                break
            }
            val common = File(cursor, "Common")
            if (File(common, "src/main/java/tk/glucodata/RemoteGlucose.java").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun legacyWidgetUsesHalfBlackBackgroundWithoutChangingAlarmRendering() {
        val remoteSource = File(moduleRoot, "src/main/java/tk/glucodata/RemoteGlucose.java").readText()
        val widgetStart = remoteSource.indexOf("final RemoteViews widgetRemote(CurrentDisplaySource.Snapshot snapshot)")
        val alarmStart = remoteSource.indexOf("final RemoteViews arrowremote(", widgetStart)
        assertTrue("widgetRemote is present", widgetStart >= 0)
        assertTrue("arrowremote follows widgetRemote", alarmStart > widgetStart)

        val widgetBody = remoteSource.substring(widgetStart, alarmStart)
        val alarmBody = remoteSource.substring(alarmStart)

        assertTrue(remoteSource.contains("LEGACY_WIDGET_DIM_COLOR = Color.argb(128, 0, 0, 0)"))
        assertTrue(widgetBody.contains("canvas.drawColor(LEGACY_WIDGET_DIM_COLOR)"))
        assertFalse(alarmBody.contains("canvas.drawColor(LEGACY_WIDGET_DIM_COLOR)"))

        val widgetSource = File(moduleRoot, "src/main/java/tk/glucodata/GlucoseWidget.java").readText()
        assertTrue(widgetSource.contains("LEGACY_WIDGET_DIM_COLOR = Color.argb(128, 0, 0, 0)"))
        assertTrue(widgetSource.contains("""setInt(R.id.content, "setBackgroundColor", LEGACY_WIDGET_DIM_COLOR)"""))
    }
}
