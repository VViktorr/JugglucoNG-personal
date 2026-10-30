package tk.glucodata

/** Source lanes exchanged with Wear: unavailable Auto never borrows Raw. */
internal object WearHistoryLanes {
    fun validMgdl(value: Float): Float =
        value.takeIf { GlucoseValuePlausibility.isPlausibleMgdl(it) } ?: 0f

    fun hasValue(autoMgdl: Float, rawMgdl: Float): Boolean =
        validMgdl(autoMgdl) > 0f || validMgdl(rawMgdl) > 0f
}
