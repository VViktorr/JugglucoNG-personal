package tk.glucodata

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Libre2NfcLiveRecoveryWiringTests {
    private val moduleRoot: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            if (File(cursor, "src/main/cpp/libre2.cpp").isFile) {
                found = cursor
                break
            }
            val common = File(cursor, "Common")
            if (File(common, "src/main/cpp/libre2.cpp").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found from ${System.getProperty("user.dir")}")
    }

    @Test
    fun nfcTrendHistoryDoesNotAdvanceLibre2LivePollCursor() {
        val libre2 = File(moduleRoot, "src/main/cpp/libre2.cpp").readText()
        val saveHistory = File(moduleRoot, "src/main/cpp/share/savehistory.cpp").readText()
        val glucoseBridge = File(moduleRoot, "src/main/cpp/g.cpp").readText()

        assertFalse(libre2.contains("saveScanTrend(data,newnu,gid"))
        assertFalse(saveHistory.contains("int saveScanTrend("))
        assertTrue(glucoseBridge.contains("const auto &trendValues = hist->gettrendsbuf"))
        assertTrue(glucoseBridge.contains("samples[trendTime].second = static_cast<jlong>(raw)"))
    }

    @Test
    fun existingRawOnlyNfcPollCanBeUpgradedByLiveBleReading() {
        val storage = File(moduleRoot, "src/main/cpp/SensorGlucoseData.hpp").readText()
        val savePollStart = storage.indexOf("bool savepoll(time_t tim")
        val nextFunction = storage.indexOf("bool savestreamonly(", savePollStart)

        assertTrue("savepoll is present", savePollStart >= 0)
        assertTrue("savestreamonly follows savepoll", nextFunction > savePollStart)

        val savePoll = storage.substring(savePollStart, nextFunction)
        assertTrue(savePoll.contains("replaceRawOnlyIndex"))
        assertTrue(savePoll.contains("if (polls[i].g)"))
        assertTrue(savePoll.contains("polls[replaceRawOnlyIndex] ="))
        assertTrue(savePoll.contains("GLU: upgraded raw-only poll"))
    }
}
