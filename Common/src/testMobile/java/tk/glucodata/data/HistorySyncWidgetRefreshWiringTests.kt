package tk.glucodata.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HistorySyncWidgetRefreshWiringTests {
    private val moduleRoot: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            if (File(cursor, "src/mobile/java/tk/glucodata/data/HistorySync.kt").isFile) {
                found = cursor
                break
            }
            val common = File(cursor, "Common")
            if (File(common, "src/mobile/java/tk/glucodata/data/HistorySync.kt").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun fullHistoryMergeRefreshesWidgetsAfterRoomSync() {
        val source = File(moduleRoot, "src/mobile/java/tk/glucodata/data/HistorySync.kt").readText()
        val mergeStart = source.indexOf("fun mergeFullSyncForSensor(serial: String)")
        assertTrue("mergeFullSyncForSensor is present", mergeStart >= 0)

        val nextFunction = source.indexOf("private fun scheduleDelayedFullMerge", mergeStart)
        assertTrue("scheduleDelayedFullMerge follows mergeFullSyncForSensor", nextFunction > mergeStart)

        val mergeBody = source.substring(mergeStart, nextFunction)
        val roomSync = mergeBody.indexOf("doSyncSensor(canonicalSerial, forceFull = true)")
        val widgetRefresh = mergeBody.indexOf("GlucoseUpdateBroadcaster.send()")

        assertTrue("full Room sync is present", roomSync >= 0)
        assertTrue("widget refresh happens after the Room sync", widgetRefresh > roomSync)
    }
}
