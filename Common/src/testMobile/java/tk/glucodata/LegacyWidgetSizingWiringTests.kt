package tk.glucodata

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyWidgetSizingWiringTests {
    private val moduleRoot: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            if (File(cursor, "src/main/java/tk/glucodata/GlucoseWidget.java").isFile) {
                found = cursor
                break
            }
            val common = File(cursor, "Common")
            if (File(common, "src/main/java/tk/glucodata/GlucoseWidget.java").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found")
    }

    @Test
    fun legacyWidgetUsesBothHostDimensions() {
        val widget = File(moduleRoot, "src/main/java/tk/glucodata/GlucoseWidget.java").readText()
        assertTrue(widget.contains("OPTION_APPWIDGET_MIN_WIDTH"))
        assertTrue(widget.contains("OPTION_APPWIDGET_MIN_HEIGHT"))
        assertTrue(widget.contains("setSize(widthdip, heightdip)"))
        assertTrue(widget.contains("new RemoteGlucose(fontsize, widthpx, heightpx"))
    }

    @Test
    fun oneByOneWidgetCanFillItsWholeCell() {
        val info = File(moduleRoot, "src/mobile/res/xml/glucose_widget_info.xml").readText()
        val layout = File(moduleRoot, "src/main/res/layout/arrowandvalue.xml").readText()
        val initial = File(moduleRoot, "src/mobile/res/layout/glucose_widget.xml").readText()
        assertTrue(info.contains("""android:targetCellWidth="1""""))
        assertTrue(info.contains("""android:targetCellHeight="1""""))
        assertTrue(info.contains("""android:minWidth="40dp""""))
        assertTrue(info.contains("""android:minHeight="40dp""""))
        assertTrue(layout.contains("""android:layout_width="match_parent""""))
        assertTrue(layout.contains("""android:layout_height="match_parent""""))
        assertTrue(layout.contains("""android:scaleType="fitXY""""))
        assertTrue(initial.contains("""android:layout_width="match_parent""""))
        assertTrue(initial.contains("""android:layout_height="match_parent""""))
    }

    @Test
    fun squareWidgetGetsDedicatedFullCellComposition() {
        val remote = File(moduleRoot, "src/main/java/tk/glucodata/RemoteGlucose.java").readText()
        assertTrue(remote.contains("requestedHeight > 0.0f"))
        assertTrue(remote.contains("compactSquare = canvasWidth <= canvasHeight * 1.45f"))
        assertTrue(remote.contains("canvasWidth / 2f, valueBaseline"))
        assertTrue(remote.contains("canvasHeight - compactInset"))
    }
}
