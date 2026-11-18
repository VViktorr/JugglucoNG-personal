package tk.glucodata.drivers.libre2

import android.content.Context
import android.nfc.tech.NfcV
import java.security.SecureRandom
import tk.glucodata.AlgNfcV
import tk.glucodata.Log
import tk.glucodata.Natives

/**
 * Entry points of the Kotlin Libre 2 driver for the existing NFC and GATT
 * code. Off by default. When on, Gen2 authentication and transport
 * decryption run here, and the native layer processes Libre 1/2 data with
 * the open `libre2_imp` backend, so no vendor library is loaded or called.
 */
object Libre2ManagedDriver {
    private const val LOG_ID = "Libre2Managed"
    private const val PREFS = "tk.glucodata_preferences"
    const val PREF_ENABLED = "libre2_kotlin_driver"

    private val secureRandom = SecureRandom()

    /** What this process runs with. Fixed at start-up; a changed switch waits for a restart. */
    @Volatile
    private var active = false

    /** True when this process uses the Kotlin Gen2 code and the open native backend. */
    @JvmStatic
    fun isEnabled(): Boolean = active

    /** The stored switch, which becomes [isEnabled] at the next app start. */
    @JvmStatic
    fun isStored(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_ENABLED, false)

    @JvmStatic
    fun setStored(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun restartPending(context: Context): Boolean = isStored(context) != active

    /**
     * Called once at start-up, before any scan or stream is processed. The
     * native layer accepts the backend choice only once per process, so
     * Kotlin and native code always agree.
     */
    @JvmStatic
    fun applyStoredSetting(context: Context) {
        val enabled = isStored(context)
        try {
            Natives.setLibre2Independent(enabled)
            active = enabled
        } catch (error: Throwable) {
            Log.stack(LOG_ID, "setLibre2Independent", error)
        }
    }

    private fun gen2Nfc(nfc: NfcV) = Libre2Gen2Nfc({ AlgNfcV.issuenfc(nfc, it) }, secureRandom::nextBytes)

    /** Gen2 activation of a new sensor. */
    @JvmStatic
    fun gen2Activate(nfc: NfcV, uid: ByteArray): Boolean = guarded("activate", false) {
        val driver = gen2Nfc(nfc)
        driver.activate(uid).also { report("activate", it, driver) }
    }

    /** Gen2 enable-streaming; stores the provisioning record and BLE address in the sensor file. */
    @JvmStatic
    fun gen2EnableStreaming(nfc: NfcV, uid: ByteArray): Boolean = guarded("enable streaming", false) {
        val driver = gen2Nfc(nfc)
        val grant = driver.enableStreaming(uid)
        report("enable streaming", grant != null, driver)
        if (grant != null) Natives.USenabledStreaming(uid, grant.provisioning.toBytes(), grant.bleAddress)
        grant != null
    }

    /** Gen2 authenticated FRAM read, or null. */
    @JvmStatic
    fun gen2ReadFram(nfc: NfcV, uid: ByteArray, offset: Int, length: Int): ByteArray? = guarded("read FRAM", null) {
        val driver = gen2Nfc(nfc)
        driver.readFram(uid, offset, length).also { report("read FRAM", it != null, driver) }
    }

    /** Starts a Gen2 BLE login for one GATT link. */
    @JvmStatic
    fun gen2BleLogin(): Libre2Gen2BleLogin = Libre2Gen2BleLogin(secureRandom::nextBytes)

    private fun report(what: String, ok: Boolean, driver: Libre2Gen2Nfc) {
        if (ok) Log.i(LOG_ID, "Gen2 $what done")
        else Log.e(LOG_ID, "Gen2 $what failed: ${driver.lastFailure}")
    }

    private inline fun <T> guarded(what: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (error: Throwable) {
        Log.stack(LOG_ID, what, error)
        fallback
    }
}

/**
 * Gen2 BLE login and frame decryption for a single GATT link. The GATT
 * callback creates one per connection attempt and closes it when the link
 * ends, so a late notification from an earlier link has nothing to act on.
 */
class Libre2Gen2BleLogin internal constructor(private val random: (ByteArray) -> Unit) {
    enum class Reply { NEED_MORE, ACCEPTED, REJECTED }

    private var exchange: Libre2Gen2Exchange? = null
    private val reply = ByteArray(Libre2Gen2Protocol.BLE_LOGIN_REPLY_BYTES)
    private var haveFirstPart = false

    /** Why the last step failed, for the connection status line. */
    var failure: String? = null
        private set

    val authenticated: Boolean get() = exchange?.authenticated == true

    /**
     * Answers the sensor's 14-byte challenge. [record] is the stored 10- or
     * 12-byte provisioning record. Returns the 19 bytes to write, or null.
     */
    @Synchronized
    fun answerChallenge(uid: ByteArray?, record: ByteArray?, challenge: ByteArray): ByteArray? {
        close()
        failure = null
        if (uid == null) return failed("No ident")
        val provisioning = Libre2Gen2Provisioning.parse(record) ?: return failed("No Auth")
        val rootSlot = provisioning.rootSlot ?: return failed("Root slot ${provisioning.storedRootSlot} unsupported")
        val opened = Libre2Gen2Exchange.open(rootSlot, uid, ble = true) ?: return failed("No sensor key")
        val hostRandom = ByteArray(Libre2Gen2Protocol.RANDOM_BYTES).also(random)
        val request = opened.buildBleRequest(provisioning, challenge, hostRandom)
        if (request == null) {
            opened.close()
            return failed("Bad challenge")
        }
        exchange = opened
        return request
    }

    /** Feeds the 7- and 18-byte halves of the sensor's answer, in order. */
    @Synchronized
    fun onReplyPart(value: ByteArray): Reply {
        val current = exchange
        if (current == null || current.authenticated) return rejected("Unexpected login reply")
        when (value.size) {
            7 -> {
                value.copyInto(reply, 0)
                haveFirstPart = true
                return Reply.NEED_MORE
            }
            18 -> {
                if (!haveFirstPart) return rejected("Login reply out of order")
                value.copyInto(reply, 7)
                haveFirstPart = false
                return if (current.acceptAuthentication(reply)) Reply.ACCEPTED else rejected("Sensor rejected login")
            }
            else -> return rejected("Login reply length ${value.size}")
        }
    }

    /** Decrypted 46-byte frame, or null when not logged in or the size is wrong. */
    @Synchronized
    fun decryptFrame(frame: ByteArray): ByteArray? = exchange?.decryptBleFrame(frame)

    @Synchronized
    fun close() {
        exchange?.close()
        exchange = null
        haveFirstPart = false
        reply.fill(0)
    }

    private fun failed(reason: String): ByteArray? {
        failure = reason
        return null
    }

    private fun rejected(reason: String): Reply {
        failure = reason
        return Reply.REJECTED
    }
}
