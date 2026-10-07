package tk.glucodata

import java.util.TreeMap

object WidgetDisplaySource {
    const val CHART_WINDOW_MS = 6L * 60L * 60L * 1000L

    @JvmStatic
    fun resolveActiveSensorSerial(preferredSerial: String? = null): String? {
        return NotificationHistorySource.resolveSensorSerial(preferredSerial ?: SensorIdentity.resolveMainSensor())
    }

    @JvmStatic
    @JvmOverloads
    fun resolveWidgetSnapshot(
        maxAgeMillis: Long = Notify.glucosetimeout,
        preferredSensorId: String? = null
    ): CurrentDisplaySource.Snapshot? {
        val activeSensorSerial = resolveActiveSensorSerial(preferredSensorId)
        return try {
            CurrentDisplaySource.resolveCurrent(maxAgeMillis, activeSensorSerial)
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun resolveViewMode(sensorName: String?): Int {
        if (sensorName.isNullOrEmpty()) {
            return 0
        }
        tk.glucodata.drivers.ManagedSensorRuntime.resolveUiSnapshot(sensorName, sensorName)
            ?.let { return it.viewMode }
        if (!SensorIdentity.hasNativeSensorBacking(sensorName)) {
            return 0
        }
        return try {
            val snapshot = Natives.getSensorUiSnapshot(sensorName)
            if (snapshot != null && snapshot.size >= 2) snapshot[1].toInt() else 0
        } catch (_: Throwable) {
            0
        }
    }

    @JvmStatic
    @JvmOverloads
    fun resolveChartHistory(
        current: CurrentDisplaySource.Snapshot? = null,
        historyWindowMs: Long = CHART_WINDOW_MS
    ): List<GlucosePoint> {
        val activeSensorSerial = current?.sensorId ?: resolveActiveSensorSerial()
        val startTimeMs = (System.currentTimeMillis() - historyWindowMs).coerceAtLeast(0L)
        val isMmol = Applic.unit == 1
        val baseHistory = try {
            // On the phone use the same merged multi-sensor timeline as the
            // dashboard chart. Falling back to the old per-sensor source keeps
            // this shared class working on Wear, where the Room bridge is absent.
            HistoryRepositoryAccess.getMergedDisplayHistoryForWidget(
                activeSensorSerial,
                startTimeMs,
                isMmol
            ) ?: NotificationHistorySource.getDisplayHistory(
                startTimeMs,
                isMmol,
                activeSensorSerial
            )
        } catch (_: Throwable) {
            emptyList()
        }

        // Home-screen widgets can be asked to render while the Room/display timeline still
        // has holes even though Libre's native history already contains the missing slots.
        // Supplement only missing minutes: recorded/display points always win, so this never
        // rewrites a minute the dashboard has already resolved or crosses a sensor handover.
        val nativeSupplement = try {
            NotificationHistorySource.getNativeDisplayHistoryForWidget(
                startTimeMs,
                isMmol,
                activeSensorSerial
            )
        } catch (_: Throwable) {
            emptyList()
        }
        val history = mergeNativeSupplement(baseHistory, nativeSupplement)
        return DisplayTrendSource.augmentHistory(history, current, activeSensorSerial, startTimeMs)
    }

    private fun mergeNativeSupplement(
        displayHistory: List<GlucosePoint>,
        nativeHistory: List<GlucosePoint>
    ): List<GlucosePoint> {
        if (nativeHistory.isEmpty()) return displayHistory
        if (displayHistory.isEmpty()) return nativeHistory.sortedBy { it.timestamp }

        val byMinute = TreeMap<Long, GlucosePoint>()
        fun minute(timestamp: Long) = timestamp / 60_000L

        // Native first, then display history overwrites the same minute. The display record
        // may contain sealed values/view mode and sensor ownership that native storage lacks.
        nativeHistory.forEach { point ->
            if (point.timestamp > 0L) byMinute[minute(point.timestamp)] = point
        }
        displayHistory.forEach { point ->
            if (point.timestamp > 0L) byMinute[minute(point.timestamp)] = point
        }
        return byMinute.values.sortedBy { it.timestamp }
    }

    @JvmStatic
    fun resolveDisplaySnapshot(
        current: CurrentDisplaySource.Snapshot?,
        historyPoints: List<GlucosePoint>,
        sensorSerial: String? = current?.sensorId
    ): CurrentDisplaySource.Snapshot? {
        if (current != null) {
            return current
        }
        val targetTimeMillis = historyPoints.lastOrNull()?.timestamp ?: return null
        val viewMode = resolveViewMode(sensorSerial)
        return CurrentDisplaySource.resolveFromLive(
            liveValueText = null,
            liveNumericValue = Float.NaN,
            rate = Float.NaN,
            targetTimeMillis = targetTimeMillis,
            sensorId = sensorSerial,
            sensorGen = 0,
            index = 0,
            source = "widget",
            recentPoints = historyPoints,
            viewMode = viewMode,
            isMmol = Applic.unit == 1
        )
    }

    @JvmStatic
    fun resolveDataState(
        current: CurrentDisplaySource.Snapshot?,
        historyPoints: List<GlucosePoint>,
        sensorSerial: String? = current?.sensorId
    ): DisplayDataState.Status {
        return DisplayDataState.resolve(
            sensorPresent = !sensorSerial.isNullOrBlank(),
            currentTimestampMillis = current?.timeMillis ?: 0L,
            latestHistoryTimestampMillis = historyPoints.lastOrNull()?.timestamp ?: 0L
        )
    }

    @JvmStatic
    fun hasCalibration(sensorSerial: String?, viewMode: Int): Boolean {
        return NightscoutCalibration.hasCalibrationForViewMode(sensorSerial, viewMode)
    }
}
