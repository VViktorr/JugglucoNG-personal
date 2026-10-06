package tk.glucodata

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Libre2HybridWiringTests {
    private val moduleRoot: File = run {
        var cursor: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var found: File? = null
        while (cursor != null) {
            if (File(cursor, "src/main/cpp/g.cpp").isFile) {
                found = cursor
                break
            }
            val common = File(cursor, "Common")
            if (File(common, "src/main/cpp/g.cpp").isFile) {
                found = common
                break
            }
            cursor = cursor.parentFile
        }
        found ?: throw AssertionError("Common module root not found")
    }

    private fun source(path: String): String = File(moduleRoot, path).readText()

    @Test
    fun duplicateLibre2FrameAdvancesParserState() {
        val native = source("src/main/cpp/g.cpp")
        val start = native.indexOf("if(algres==ALGDUP_VALUE")
        val end = native.indexOf("if(algres)", start + 1)
        assertTrue(start >= 0 && end > start)
        val block = native.substring(start, end)
        assertTrue(block.contains("sdata->setstate(newstate)"))
        assertFalse(block.contains("delete newstate"))
    }

    @Test
    fun libre2KeepsForkOwnershipHooksAroundUpstreamGattCore() {
        val superGatt = source("src/main/java/tk/glucodata/SuperGattCallback.java")
        val libre2 = source("src/main/java/tk/glucodata/Libre2GattCallback.java")
        assertTrue(superGatt.contains("private final Object gattLock = new Object()"))
        assertTrue(superGatt.contains("public boolean reconnect(long now,long delay)"))
        assertTrue(superGatt.contains("connectDeadline.completed(gatt)"))
        assertTrue(superGatt.contains("WearSensorClaim.onLocalGattDisconnected(SerialNumber)"))
        assertTrue(libre2.contains("acceptConnectionStateChange(bluetoothGatt,newState)"))
        assertTrue(libre2.contains("super.onConnectionStateChange(bluetoothGatt, status, newState)"))
    }

    @Test
    fun classicLibre2StillUsesForkResultPipeline() {
        val superGatt = source("src/main/java/tk/glucodata/SuperGattCallback.java")
        assertFalse(superGatt.contains("handleLibre2GlucoseResultUpstream"))
        assertTrue(superGatt.contains("handleGlucoseResultInternal(res, timmsec, 0, Float.NaN)"))
        assertTrue(superGatt.contains("markLocalReadingAccepted(timmsec)"))
    }

    @Test
    fun ownershipAndCloneGatesStillApplyToLibre2() {
        val bluetooth = source("src/main/java/tk/glucodata/SensorBluetooth.java")
        assertTrue(bluetooth.contains("SensorOwnershipRuntime.blocksLocalConnection(cb.SerialNumber)"))
        assertTrue(bluetooth.contains("mirroredOverClone(cb"))
        assertFalse(bluetooth.contains("!(cb instanceof Libre2GattCallback)"))
        assertTrue(bluetooth.contains("cb.reconnect(now, delay)"))
    }

    @Test
    fun personalNfcRecoveryAndLatestUiHooksSurvive() {
        val alg = source("src/main/java/tk/glucodata/AlgNfcV.java")
        val scan = source("src/main/java/tk/glucodata/ScanNfcV.java")
        val storage = source("src/main/cpp/SensorGlucoseData.hpp")
        assertTrue(alg.contains("Unknown Libre generation, trying Gen2 BLE enable fallback"))
        assertTrue(scan.contains("HistorySyncAccess.mergeFullSyncForSensor(scannedSensorIdent)"))
        assertTrue(scan.contains("R.string.sensor_finished_scan_new"))
        assertTrue(storage.contains("replaceRawOnlyIndex"))
        assertTrue(storage.contains("GLU: upgraded raw-only poll"))
    }
}
