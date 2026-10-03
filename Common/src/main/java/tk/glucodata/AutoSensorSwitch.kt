package tk.glucodata

import android.content.Context

/**
 * Legacy automatic-routing preference.
 *
 * In companion mode the phone is the only device allowed to read the physical
 * sensor. The watch receives mirrored glucose over Wear OS and must stay off the
 * sensor even when the phone is unreachable. Direct sensor on watch remains the
 * only explicit opt-in that may arm local sensor Bluetooth on Wear OS.
 */
object AutoSensorSwitch {
    private const val LOG_ID = "AutoSensorSwitch"
    private const val PREFS = "tk.glucodata_preferences"

    /** Phone-owned; mirrored to the watch by [WearToggleSync]. */
    const val PREF_KEY = "wear_auto_sensor_switch"

    /** [WearToggleSync] scope-`p` id for the mirrored copy. */
    const val TOGGLE_ID = "autoswitch"

    @JvmStatic
    fun isEnabled(): Boolean = runCatching {
        Applic.app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(PREF_KEY, false)
    }.getOrNull() ?: false

    /**
     * Writes the setting on this device and brings its radio into line.
     *
     * Called on the phone when the user flips the switch, and on the watch when
     * the mirrored value arrives.
     */
    @JvmStatic
    fun setEnabled(enabled: Boolean) {
        runCatching {
            Applic.app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean(PREF_KEY, enabled)
                ?.apply()
        }.onFailure { Log.stack(LOG_ID, "persist($enabled)", it) }
        Log.i(LOG_ID, "automatic sensor switching $enabled")
        applyRadioState(enabled)
        UiRefreshBus.requestStatusRefresh()
    }

    /**
     * A stale mirrored automatic-switch preference must not re-arm sensor
     * Bluetooth on the watch after restart. Direct mode owns the only exception.
     */
    @JvmStatic
    fun restoreOnStart() {
        if (!Applic.isWearable || WearSensorClaim.isDirectRequested()) return
        if (isEnabled()) {
            Log.i(LOG_ID, "automatic sensor switching ignored on watch; companion mode stays phone-only")
        }
        applyRadioState(false)
    }

    /**
     * Automatic switching never arms the watch radio. A watch in explicit
     * Direct sensor mode is managed by [WearSensorClaim] and is left untouched;
     * every other watch is kept in companion-only mode.
     */
    private fun applyRadioState(enabled: Boolean) {
        if (!Applic.isWearable || WearSensorClaim.isDirectRequested()) return
        val context = MainActivity.thisone ?: Applic.app ?: return
        if (enabled) {
            Log.i(LOG_ID, "ignoring automatic sensor switching on watch; Direct sensor mode is required")
        }
        runCatching { Applic.setbluetooth(context, false) }
            .onFailure { Log.stack(LOG_ID, "setbluetooth(false)", it) }
    }
}
