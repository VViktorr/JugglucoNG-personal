package tk.glucodata.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactGlucoseWidgetTests {
    @Test
    fun trendGlyphsMatchSharedTrendConvention() {
        assertEquals("", CompactGlucoseWidgetText.trendArrow(Float.NaN))
        assertEquals("→", CompactGlucoseWidgetText.trendArrow(0f))
        assertEquals("↗", CompactGlucoseWidgetText.trendArrow(1f))
        assertEquals("↑", CompactGlucoseWidgetText.trendArrow(1.6f))
        assertEquals("↑↑", CompactGlucoseWidgetText.trendArrow(2.1f))
        assertEquals("↘", CompactGlucoseWidgetText.trendArrow(-1f))
        assertEquals("↓", CompactGlucoseWidgetText.trendArrow(-1.6f))
        assertEquals("↓↓", CompactGlucoseWidgetText.trendArrow(-2.1f))
    }

    @Test
    fun compactWidgetIsRegisteredForGlucoseRefreshes() {
        val moduleRoot = findModuleRoot()
        val manifest = File(moduleRoot, "src/mobile/AndroidManifest.xml").readText()
        val provider = File(moduleRoot, "src/mobile/res/xml/compact_glucose_widget_info.xml").readText()
        val source = File(moduleRoot, "src/mobile/java/tk/glucodata/widget/CompactGlucoseWidget.kt").readText()

        assertTrue(manifest.contains(".widget.CompactGlucoseWidgetReceiver"))
        assertTrue(manifest.contains("tk.glucodata.action.GLUCOSE_UPDATE"))
        assertTrue(manifest.contains("@xml/compact_glucose_widget_info"))
        assertTrue(provider.contains("android:minHeight=\"56dp\""))
        assertTrue(source.contains("resolved?.primaryStr"))
        assertTrue(source.contains("DateFormat.getTimeFormat(context)"))
        assertTrue(source.contains("Color(0xB3000000.toInt())"))
    }

    private fun findModuleRoot(): File {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (cursor != null) {
            if (File(cursor, "src/mobile/AndroidManifest.xml").isFile) return cursor
            val common = File(cursor, "Common")
            if (File(common, "src/mobile/AndroidManifest.xml").isFile) return common
            cursor = cursor.parentFile
        }
        throw AssertionError("Common module root not found from ${System.getProperty("user.dir")}")
    }
}
