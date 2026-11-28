package tk.glucodata

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit
import tk.glucodata.drivers.libre2.Libre2Gen1Crypto

/** Owns the Gen1 BLE lifecycle on the shared Android GATT transport. */
@SuppressLint("MissingPermission")
internal class Libre2Gen1KotlinGattCallback(serial: String, nativeStream: Long) :
    SuperGattCallback(serial, nativeStream, 1) {

    private enum class Phase { IDLE, DISCOVERING, LOGIN, SUBSCRIBING, STREAMING }

    private var phase = Phase.IDLE
    private var subscriptionQueued = false
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

    private var disconnectingGatt: BluetoothGatt? = null
    private var retryDelayMs = 0L
    private var relinkAt = 0L
    private var linkStartedAt = -1L
    private var attemptStartedAt = -1L
    private var lastFrameAt = -1L
    private var packetGapAt = -1L
    private var failures = 0
    private var packets = 0
    private var attempt = 0
    private var recovered = false
    private var recovering = false
    private var recoverySkipLogged = false
    private var recoveryWakeLock: PowerManager.WakeLock? = null
    private var lastCompleted = "connect-queued"
    private var lastResult = "none"
    private var firstReadingPending = true
    private var historyRows = 0L
    private val frameStats = LongArray(2)
    private val disconnectDeadline = deadline<BluetoothGatt>(::retryAfterDisconnect)
    private val streamDeadline = deadline<BluetoothGatt>(::streamSilent)
    private val escalationDeadline = deadline<Any> { hardRecover("stream-silence") }
    private val recoveryDeadline = deadline<Any> {
        recovering = false
        libre2Diag("hard-recovery-resume", "")
        if (!connectDevice(0L)) releaseRecoveryWakeLock()
    }

    private fun <T : Any> deadline(expired: (T) -> Unit) = GattConnectDeadline<T>(
        this,
        { task, delay ->
            val pending = Applic.scheduler.schedule(task, delay, TimeUnit.MILLISECONDS)
            GattConnectDeadline.Cancellation { pending.cancel(false) }
        },
        expired,
    )

    private fun ownsConnection(): Boolean = !stop && dataptr != 0L &&
        !CloneSensorRegistry.isCloneSensor(SerialNumber) &&
        !SensorOwnershipRuntime.blocksLocalConnection(SerialNumber)

    private fun acceptGattCallback(gatt: BluetoothGatt): Boolean =
        gatt === mBluetoothGatt && disconnectingGatt == null && !recovering && ownsConnection()

    override fun useAutoConnect(): Boolean = false

    override fun connectionAttemptTimeoutMillis(): Long = 120_000L

    override fun onConnectionAttemptTimeout() {
        libre2Diag("connect-timeout", "")
        noteFailedAttempt()
        endAttempt("connect-timeout")
    }

    @Synchronized
    override fun connectDevice(delayMillis: Long): Boolean {
        if (!ownsConnection() || recovering) return false
        val now = SystemClock.elapsedRealtime()
        mBluetoothGatt?.let { gatt ->
            if (streamingGatt !== gatt || disconnectingGatt != null ||
                lastFrameAt < 0L || now - lastFrameAt >= STREAM_SILENCE_MS
            ) requestDisconnect(gatt, delayMillis)
            return true
        }
        if (!Applic.isWearable && !recovered && failures >= 3 &&
            packetGapAt >= 0L && now - packetGapAt >= STREAM_SILENCE_MS
        ) {
            hardRecover("repeated-attempts-without-packet")
            return true
        }
        val wait = (relinkAt - now).coerceAtLeast(0L)
        if (!super.connectDevice(maxOf(delayMillis, wait))) return false
        if (packetGapAt < 0L) packetGapAt = now
        attemptStartedAt = now
        packets = 0
        attempt++
        firstReadingPending = true
        lastCompleted = "connect-queued"
        lastResult = "none"
        libre2Diag("connect-queued", "delayMs=${maxOf(delayMillis, wait)} relinkWaitMs=$wait")
        return true
    }

    @Synchronized
    override fun reconnect(now: Long): Boolean {
        val staleBefore = now - showtime + 20L
        if (hasLocallyConnectedGatt() && charcha[0] >= staleBefore) return true
        if (charcha[1] >= staleBefore || connectTime >= now - 60_000L) return true
        noteLossOfSignal(now)
        return connectDevice(0L)
    }

    @Synchronized
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (gatt !== mBluetoothGatt || recovering) return
        if (!ownsConnection()) {
            close()
            return
        }
        if (disconnectingGatt != null && newState != BluetoothProfile.STATE_DISCONNECTED) return
        if (!acceptConnectionAttemptCallback(gatt, newState)) return
        noteFirstGattCallback("onConnectionStateChange", gatt)
        releaseRecoveryWakeLock()
        libre2Diag("gatt-state", "status=$status state=$newState")
        val now = System.currentTimeMillis()
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            if (linkStartedAt >= 0L) return
            super.onConnectionStateChange(gatt, status, newState)
            linkStartedAt = SystemClock.elapsedRealtime()
            constatchange[0] = now
            setConStatus(status)
            lastCompleted = "connected"
            streamDeadline.arm(gatt, STREAM_SILENCE_MS)
            try {
                setpriority(gatt)
                val queued = discoverLibreServices(gatt)
                reportGen1Step(null, "discovery-request-$queued")
                if (!queued) fail(gatt, "discovery-not-queued")
            } catch (error: RuntimeException) {
                Log.stack(TAG, "service discovery failed", error)
                fail(gatt, "discovery-exception")
            }
            return
        }
        if (newState != BluetoothProfile.STATE_DISCONNECTED && status == BluetoothGatt.GATT_SUCCESS) return
        constatchange[1] = now
        if (constatstatusstr != "Loss of signal") {
            val wasUp = linkStartedAt >= 0L
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setConStatus(status, wasUp && !(status == 22 && disconnectingGatt === gatt))
            } else if (wasUp && disconnectingGatt == null) {
                recordEstablishedLinkDrop(0)
            }
        }
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            super.onConnectionStateChange(gatt, status, newState)
            if (status == 19 && subscriptionQueued) {
                Natives.resetbluetooth(dataptr)
            }
            endAttempt("status=$status")
            retryAfterDisconnect(gatt)
        } else {
            requestDisconnect(gatt, 0L)
        }
    }

    private fun requestDisconnect(gatt: BluetoothGatt, delayMillis: Long) {
        if (gatt !== mBluetoothGatt) return
        retryDelayMs = delayMillis
        if (disconnectingGatt === gatt) return
        disconnectingGatt = gatt
        handshakeDeadline.cancel()
        streamDeadline.cancel()
        disconnectDeadline.arm(gatt, 2_000L)
        try {
            super.disconnect()
        } catch (error: RuntimeException) {
            Log.stack(TAG, "disconnect failed", error)
            retryAfterDisconnect(gatt)
        }
    }

    private fun retryAfterDisconnect(gatt: BluetoothGatt) {
        if (gatt !== mBluetoothGatt) return
        val delay = retryDelayMs
        endAttempt("disconnect-timeout")
        noteFailedAttempt()
        // Closing holds the relink deadline before clearing the established link.
        closeGattTransport()
        connectDevice(delay)
    }

    private fun noteFailedAttempt() {
        if (packets != 0 || !ownsConnection() || Applic.isWearable) return
        if (!recovered) {
            if (packetGapAt >= 0L) failures++
        } else if (!recoverySkipLogged) {
            recoverySkipLogged = true
            libre2Diag("recovery-skipped", "reason=already-used-in-this-packet-gap")
        }
    }

    private fun streamSilent(gatt: BluetoothGatt) {
        if (!acceptGattCallback(gatt)) return
        libre2Diag("stream-silence", "timeoutMs=$STREAM_SILENCE_MS")
        if (!Applic.isWearable && !recovered && !escalationArmed) {
            escalationArmed = true
            escalationDeadline.arm(Any(), STREAM_SILENCE_MS)
        }
        noteLossOfSignal(System.currentTimeMillis())
        requestDisconnect(gatt, 0L)
    }

    private var escalationArmed = false

    private fun hardRecover(reason: String) {
        if (Applic.isWearable || !ownsConnection() || recovered || recovering) return
        recovered = true
        recovering = true
        escalationDeadline.cancel()
        libre2Diag("hard-recovery", "reason=$reason")
        recoveryWakeLock = acquireWakeLock("Libre2Recovery", 15_000L)
        try {
            endAttempt("hard-recovery")
            closeGattTransport()
            Natives.resetbluetooth(dataptr)
        } catch (error: RuntimeException) {
            Log.stack(TAG, "recovery failed", error)
        } finally {
            recoveryDeadline.arm(Any(), 6_000L)
        }
    }

    private fun releaseRecoveryWakeLock() {
        Applic.releasewakelock(recoveryWakeLock)
        recoveryWakeLock = null
    }

    private fun cancelRecovery() {
        escalationDeadline.cancel()
        recoveryDeadline.cancel()
        escalationArmed = false
        recovering = false
        failures = 0
        packetGapAt = -1L
        releaseRecoveryWakeLock()
    }

    private fun endAttempt(reason: String) {
        if (attemptStartedAt < 0L) return
        libre2Diag("attempt-summary", "end=$reason lastCompleted=$lastCompleted lastGattResult=$lastResult")
        attemptStartedAt = -1L
    }

    private fun libre2Diag(stage: String, detail: String) {
        if (!Log.doLog && stage != "hard-recovery") return
        val now = SystemClock.elapsedRealtime()
        fun age(timestamp: Long) = if (timestamp < 0L) -1L else now - timestamp
        Log.i(TAG, "${LogPrivacy.sensorAlias(SerialNumber)} LIBRE2_DIAG link=$linkId attempt=$attempt " +
            "gen=1 driver=kotlin-gen1 stage=$stage packets=$packets linkAgeMs=${age(linkStartedAt)} " +
            "packetAgeMs=${age(lastFrameAt)} attemptAgeMs=${age(attemptStartedAt)} " +
            "gapMs=${age(packetGapAt)} failedAttempts=$failures hardUsed=$recovered $detail")
    }

    private fun reportGen1Step(step: String?, result: String) {
        if (step != null) lastCompleted = step
        lastResult = result
        libre2Diag("gen1-operation", "result=$result")
    }

    private fun acquireWakeLock(name: String, timeout: Long): PowerManager.WakeLock? =
        runCatching {
            val power = Applic.app.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Juggluco::$name").apply {
                setReferenceCounted(false)
                acquire(timeout)
            }
        }.getOrNull()

    private fun processFrame(gatt: BluetoothGatt, frame: ByteArray) {
        lastFrameAt = SystemClock.elapsedRealtime()
        packetGapAt = lastFrameAt
        packets++
        recovered = false
        recoverySkipLogged = false
        escalationArmed = false
        failures = 0
        escalationDeadline.cancel()
        streamDeadline.arm(gatt, STREAM_SILENCE_MS)
        clearLossOfSignalState()
        lastCompleted = "frame-decoded"
        val now = System.currentTimeMillis()
        val lock = if (Natives.hasRootcheck()) acquireWakeLock("Libre2Frame", Applic.GLUCOSE_WAKELOCK_TIMEOUT_MS) else null
        try {
            val result = Natives.processTooth(dataptr, frame, if (firstReadingPending) frameStats else null)
            if (firstReadingPending) historyRows += frameStats[1]
            val outcome = when (result) {
                0L -> "no-result"
                1L -> "duplicate"
                else -> {
                    lastCompleted = "live-reading"
                    if (firstReadingPending) {
                        firstReadingPending = false
                        val prior = frameStats[0] * 1_000L
                        val gap = if (prior in 1L..now) now - prior else -1L
                        val processAge = SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
                        libre2Diag("first-live-reading", "readingGapMs=$gap sinceProcessStartMs=$processAge historyRowsSaved=$historyRows")
                        historyRows = 0L
                    }
                    "handled"
                }
            }
            libre2Diag("ble-frame", "bytes=${frame.size} outcome=$outcome")
            if (result != 1L) handleGlucoseResult(result, now)
        } finally {
            Applic.releasewakelock(lock)
        }
    }

    private val linkId = nextLinkId.incrementAndGet()

    override fun openGatt(device: BluetoothDevice): BluetoothGatt? =
        device.connectGatt(Applic.app, false, this, BluetoothDevice.TRANSPORT_LE)

    private fun discoverLibreServices(gatt: BluetoothGatt): Boolean {
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
        subscriptionQueued = true
        wrotepass[0] = System.currentTimeMillis()
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
        subscriptionQueued = false
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
        processFrame(gatt, frame)
    }

    override fun isRssiLinkConnected(gatt: BluetoothGatt): Boolean =
        streamingGatt === gatt && super.isRssiLinkConnected(gatt)

    override fun onClosingGattTransport() {
        if (linkStartedAt >= 0L) relinkAt = SystemClock.elapsedRealtime() + RELINK_DELAY_MS
        endAttempt("closed")
        linkStartedAt = -1L
        disconnectingGatt = null
        retryDelayMs = 0L
        disconnectDeadline.cancel()
        streamDeadline.cancel()
        readrssi = 9999
        clearSession()
    }

    @Synchronized
    override fun disconnect() {
        cancelRecovery()
        val gatt = mBluetoothGatt
        if (gatt == null) super.disconnect() else requestDisconnect(gatt, 0L)
    }

    @Synchronized
    override fun setPause(pause: Boolean) {
        super.setPause(pause)
        if (pause) {
            cancelRecovery()
            endAttempt("paused")
            closeGattTransport()
        }
    }

    @Synchronized
    override fun close() {
        cancelRecovery()
        endAttempt("closed")
        super.close()
    }

    override fun free() {
        super.free()
        LossOfSensorAlarm.cancelalarm()
    }

    override fun getService(): UUID = SERVICE

    override fun matchDeviceName(deviceName: String?, address: String?): Boolean =
        deviceName?.startsWith("ABBOTT") == true && deviceName.substring(6) == SerialNumber

    private fun clearSession() {
        handshakeDeadline.cancel()
        phase = Phase.IDLE
        subscriptionQueued = false
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
        const val TAG = "Libre2GattCallback"
        const val HANDSHAKE_TIMEOUT_MS = 12_000L
        const val RELINK_DELAY_MS = 5_000L
        const val STREAM_SILENCE_MS = 150_000L
        val SERVICE: UUID = UUID.fromString("0000fde3-0000-1000-8000-00805f9b34fb")
        val nextLinkId = AtomicInteger()
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
