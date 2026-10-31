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
        found ?: throw AssertionError("Common module root not found")
    }

    @Test
    fun nfcTrendBackfillUsesSeparateStoreWithoutAdvancingLiveBleCursor() {
        val libre2 = File(moduleRoot, "src/main/cpp/libre2.cpp").readText()
        val saveHistory = File(moduleRoot, "src/main/cpp/share/savehistory.cpp").readText()
        val storage = File(moduleRoot, "src/main/cpp/SensorGlucoseData.hpp").readText()
        val bridge = File(moduleRoot, "src/main/cpp/g.cpp").readText()

        assertTrue(libre2.contains("saveScanTrend(data,newnu,gid"))

        val saveStart = saveHistory.indexOf("int saveScanTrend(")
        val saveEnd = saveHistory.indexOf("extern std::vector<int> usedsensors", saveStart)
        assertTrue("NFC trend persistence function exists", saveStart >= 0)
        assertTrue("NFC trend persistence function has an end", saveEnd > saveStart)
        val saveTrend = saveHistory.substring(saveStart, saveEnd)
        assertTrue(saveTrend.contains("save.saveNfcRawHistory(rows)"))
        assertFalse("NFC backfill must not write into the live poll cursor",
            saveTrend.contains("save.savepoll("))

        assertTrue(storage.contains("NfcRawHistory::Result saveNfcRawHistory"))
        assertTrue(storage.contains("std::vector<NfcRawSample> getNfcRawHistory"))

        assertTrue(bridge.contains("appendNfcRawHistory(hist, starttime, samples)"))
        val appendStart = bridge.indexOf("static void appendNfcRawHistory(")
        val appendEnd = bridge.indexOf("static jlongArray historySamplesToJava", appendStart)
        assertTrue("NFC history bridge exists", appendStart >= 0)
        assertTrue("NFC history bridge has an end", appendEnd > appendStart)
        val appendRawHistory = bridge.substring(appendStart, appendEnd)
        assertTrue(appendRawHistory.contains("hist->getNfcRawHistory()"))
        assertTrue(appendRawHistory.contains("value.second <= 0"))
    }
}
