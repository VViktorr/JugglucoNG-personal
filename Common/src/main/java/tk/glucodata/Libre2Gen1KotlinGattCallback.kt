package tk.glucodata

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import java.util.concurrent.TimeUnit
import tk.glucodata.drivers.libre2.Libre2Gen1Crypto

/** Local to the device that owns the sensor. A new callback reads the selection at creation. */
internal object Libre2Gen1DriverSettings {
    private const val PREFS = "libre2_gen1_driver"
    private const val KOTLIN = "kotlin"

    @JvmStatic
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KOTLIN, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KOTLIN, enabled).apply()
    }
}

/** Owns the Gen1 handshake and stream on the shared Libre 2 GATT transport. */
@SuppressLint("MissingPermission")
internal class Libre2Gen1KotlinGattCallback(serial: String, nativeStream: Long) :
    Libre2GattCallback(serial, nativeStream) {

    private enum class Phase { IDLE, DISCOVERING, LOGIN, SUBSCRIBING, STREAMING }

    private var phase = Phase.IDLE
    private var rawCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var streamingGatt: BluetoothGatt? = null
    private var frameUid: ByteArray? = null
    private val frames = Libre2Gen1FrameAssembler()
    private val handshakeDeadline = GattConnectDeadline<BluetoothGatt>(
        this,
        { task, delay ->
            val pending = Applic.scheduler.schedule(task, delay, TimeUnit.MILLISECONDS)
            GattConnectDeadline.Cancellation { pending.cancel(false) }
        },
        { gatt -> if (acceptGattCallback(gatt)) fail(gatt, "handshake-timeout") },
    )

    override fun driverLabel(): String = "kotlin-gen1"

    override fun discoverLibreServices(gatt: BluetoothGatt): Boolean {
        clearSession()
        phase = Phase.DISCOVERING
        val queued = gatt.discoverServices()
        if (queued) handshakeDeadline.arm(gatt, HANDSHAKE_TIMEOUT_MS)
        return queued
    }

    @Synchronized
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (!acceptGattCallback(gatt) || phase != Phase.DISCOVERING) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail(gatt, "service-discovery-$status", status)
            return
        }
        reportGen1Step("services-discovered", "service-discovery-$status")
        val service = gatt.getService(getService())
        val login = service?.getCharacteristic(mCharacteristicUUID_BLELogin)
        rawCharacteristic = service?.getCharacteristic(mCharacteristicUUID_CompositeRawData)
        if (login == null || rawCharacteristic == null) {
            fail(gatt, "characteristics-unavailable")
            return
        }
        val uid = ByteArray(8)
        val patch = ByteArray(6)
        val parameters = IntArray(2)
        val unlock = if (Natives.reserveLibre2Gen1Unlock(dataptr, uid, patch, parameters)) {
            Libre2Gen1Crypto.streamingUnlockPayload(uid, patch, parameters[0], parameters[1])
        } else {
            null
        }
        if (unlock == null) {
            fail(gatt, "unlock-unavailable")
            return
        }
        frameUid = uid
        phase = Phase.LOGIN
        val result = gatt.writeCharacteristic(login, unlock, login.writeType)
        reportGen1Step(null, "login-write-$result")
        if (result != BluetoothStatusCodes.SUCCESS) fail(gatt, "login-write-$result")
    }

    @Synchronized
    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        if (!acceptGattCallback(gatt) || phase != Phase.LOGIN || characteristic.uuid != mCharacteristicUUID_BLELogin) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail(gatt, "characteristic-write-$status", status)
            return
        }
        reportGen1Step("characteristic-written", "characteristic-write-$status")
        val raw = rawCharacteristic ?: return
        val descriptor = raw.getDescriptor(mCharacteristicConfigDescriptor)
        if (descriptor == null || !gatt.setCharacteristicNotification(raw, true)) {
            fail(gatt, "notification-unavailable")
            return
        }
        phase = Phase.SUBSCRIBING
        val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        reportGen1Step(null, "stream-subscribe-$result")
        if (result != BluetoothStatusCodes.SUCCESS) {
            fail(gatt, "stream-subscribe-$result")
            return
        }
        gen1SubscriptionQueued()
    }

    @Synchronized
    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        if (!acceptGattCallback(gatt) || phase != Phase.SUBSCRIBING ||
            descriptor.uuid != mCharacteristicConfigDescriptor ||
            descriptor.characteristic.uuid != mCharacteristicUUID_CompositeRawData
        ) return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            fail(gatt, "descriptor-write-$status", status)
            return
        }
        phase = Phase.STREAMING
        handshakeDeadline.cancel()
        reportGen1Step("stream-subscribed", "descriptor-write-$status")
    }

    @Synchronized
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (!acceptGattCallback(gatt) || characteristic.uuid != mCharacteristicUUID_CompositeRawData ||
            (phase != Phase.SUBSCRIBING && phase != Phase.STREAMING)
        ) return
        libre2Diag("stream-fragment", "bytes=${value.size}")
        val frame = frames.append(value) ?: return
        // Some Android stacks deliver the first notification before the descriptor callback.
        streamingGatt = gatt
        frameUid?.let { uid ->
            if (Log.doLog) {
                val valid = Libre2Gen1Crypto.decryptBle(uid, frame) != null
                libre2Diag("gen1-kotlin-checksum", "valid=$valid")
            }
            frameUid = null
        }
        processCompleteFrame(frame)
    }

    override fun isRssiLinkConnected(gatt: BluetoothGatt): Boolean =
        streamingGatt === gatt && super.isRssiLinkConnected(gatt)

    override fun onClosingGattTransport() {
        clearSession()
        super.onClosingGattTransport()
    }

    @Synchronized
    override fun setPause(pause: Boolean) {
        if (pause) clearSession()
        super.setPause(pause)
    }

    private fun clearSession() {
        handshakeDeadline.cancel()
        phase = Phase.IDLE
        streamingGatt = null
        rawCharacteristic = null
        frameUid = null
        frames.reset()
    }

    private fun fail(gatt: BluetoothGatt, result: String, status: Int? = null) {
        handshakeDeadline.cancel()
        handshake = result
        wrotepass[1] = System.currentTimeMillis()
        reportGen1Step(null, result)
        status?.let { setConStatus(it, false) }
        requestDisconnect(gatt, 0L)
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 12_000L
    }
}

internal class Libre2Gen1FrameAssembler {
    private val frame = ByteArray(46)
    private var offset = 0

    fun reset() {
        offset = 0
    }

    fun append(fragment: ByteArray): ByteArray? {
        when (fragment.size) {
            20 -> offset = 0
            18 -> if (offset != 20) return null
            8 -> if (offset != 38) {
                reset()
                return null
            }
            else -> return null
        }
        fragment.copyInto(frame, offset)
        offset += fragment.size
        if (offset != frame.size) return null
        reset()
        return frame
    }
}
