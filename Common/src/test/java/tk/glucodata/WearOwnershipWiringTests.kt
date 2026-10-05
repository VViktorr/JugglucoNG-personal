package tk.glucodata

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the phone-first Wear ownership boundary.
 *
 * /netinfo is a transport/status exchange, not an ownership command. Physical
 * sensor BLE on Wear may only be armed by the explicit /bluetooth Direct-mode
 * command, while the phone yields only from its recorded Direct assignment.
 */
class WearOwnershipWiringTests {

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "Common/src/main/cpp/watch/netinfo.cpp").isFile) return dir
            dir = dir.parentFile
        }
        throw AssertionError("Common/src not found from ${System.getProperty("user.dir")}")
    }

    private fun read(path: String): String = File(repoRoot(), path).readText()

    private fun section(source: String, start: String, end: String): String {
        val at = source.indexOf(start)
        assertTrue("missing: $start", at >= 0)
        val stop = source.indexOf(end, at + start.length)
        assertTrue("missing: $end", stop > at)
        return source.substring(at, stop)
    }

    @Test
    fun incomingNetinfoCannotTogglePhysicalSensorBluetooth() {
        val setNetInfo = section(
            read("Common/src/main/cpp/watch/netinfo.cpp"),
            "fromjava(setmynetinfo)",
            "struct ringnouri",
        )
        assertFalse(
            "/netinfo must not write nobluetooth; explicit Direct mode owns sensor BLE",
            setNetInfo.contains("settings->data()->nobluetooth="),
        )
    }

    @Test
    fun bluetoothMessageIsTheExplicitWearRadioGate() {
        val block = section(
            read("Common/src/main/java/tk/glucodata/MessageReceiver.kt"),
            "WearMessagePath.BLUETOOTH -> {",
            "WearMessagePath.ASKFORSTART ->",
        )
        assertTrue(block.contains("WearSensorClaim.setDirectRequested(on)"))
        assertTrue(block.contains("Applic.setbluetooth(context,on"))
        assertFalse(
            "legacy automatic switching must not keep Wear sensor BLE armed",
            block.contains("AutoSensorSwitch.isEnabled()"),
        )
    }

    @Test
    fun phoneRoutingChoiceReconcilesOwnershipImmediately() {
        val interop = section(
            read("Common/src/mobile/java/tk/glucodata/WatchInterop.kt"),
            "fun applyWearNodeRouting(",
            "fun setAutoSensorSwitch(",
        )
        val recorded = interop.indexOf("WearRoutingRequest.record(")
        val reconciled = interop.indexOf("SensorOwnershipRuntime.onRoutingRequestChanged()")
        assertTrue("Direct route must be recorded before ownership reconcile", recorded >= 0 && reconciled > recorded)

        val runtime = section(
            read("Common/src/main/java/tk/glucodata/SensorOwnershipRuntime.kt"),
            "fun onRoutingRequestChanged()",
            "fun requestPhoneOwnership(",
        )
        assertTrue(runtime.contains("reconcile()"))
    }
}
