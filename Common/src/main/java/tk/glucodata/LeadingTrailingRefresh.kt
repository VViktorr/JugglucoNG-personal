package tk.glucodata

/** Immediate first update and one trailing update; bursts never lose their final state. */
internal class LeadingTrailingRefresh(
    private val intervalMs: Long,
    private val clock: () -> Long,
    private val schedule: (Long, () -> Unit) -> Cancellation,
    private val action: () -> Unit,
) {
    fun interface Cancellation {
        fun cancel()
    }

    private var lastUpdateMs: Long? = null
    private var pending: Cancellation? = null
    private var generation = 0L

    init {
        require(intervalMs > 0L)
    }

    /** [force] is for a completed backfill, whose newest reading must be shown now. */
    fun request(force: Boolean = false) {
        var runNow = false
        synchronized(this) {
            val now = clock()
            val last = lastUpdateMs
            if (force || last == null || now - last >= intervalMs) {
                pending?.cancel()
                pending = null
                generation++
                lastUpdateMs = now
                runNow = true
            } else if (pending == null) {
                val token = ++generation
                pending = schedule(intervalMs - (now - last)) { deliver(token) }
            }
        }
        // Do not hold a producer/scheduler lock while dispatching external UI work.
        if (runNow) action()
    }

    private fun deliver(token: Long) {
        synchronized(this) {
            // Cancellation can race with an already dispatched scheduler callback.
            if (token != generation) return
            generation++
            pending = null
            lastUpdateMs = clock()
        }
        action()
    }
}
