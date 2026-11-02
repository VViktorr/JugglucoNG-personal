package tk.glucodata

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object UiRefreshBus {
    sealed interface Event {
        data object DataChanged : Event
        data object StatusOnly : Event
    }

    private val _events = MutableSharedFlow<Event>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _revision = MutableStateFlow(0L)

    val events = _events.asSharedFlow()
    val revision = _revision.asStateFlow()

    private fun bumpRevision() {
        _revision.update { it + 1L }
    }

    @JvmStatic
    fun requestDataRefresh() {
        bumpRevision()
        _events.tryEmit(Event.DataChanged)
        Notify.scheduleDataChangedRefresh()
        GlucoseUpdateBroadcaster.send(Applic.app)
        refreshWatchFaceSurfaces()
    }

    // Keep the first update immediate and deliver the newest state after a burst.
    // Unlike dropping requests, this cannot strand a complication at an early backfill chunk.
    private val complicationRefresh by lazy {
        LeadingTrailingRefresh(
            intervalMs = 20_000L,
            clock = SystemClock::elapsedRealtime,
            schedule = { delay, task ->
                val future = Applic.scheduler.schedule({ task() }, delay, TimeUnit.MILLISECONDS)
                LeadingTrailingRefresh.Cancellation { future.cancel(false) }
            },
            action = { runCatching { GlucoseValueRefreshAccess.get()?.updateAll() } },
        )
    }

    private fun refreshWatchFaceSurfaces() {
        if (Applic.isWearable) complicationRefresh.request()
    }

    @JvmStatic
    fun requestStatusRefresh() {
        _events.tryEmit(Event.StatusOnly)
        runCatching { Floating.invalidatefloat() }
        // The phone ongoing notification reconciles through the same coalesced visual
        // path as data changes (bounded debounce in Notify); status-only work never
        // broadcasts, alarms, or retriggers alertwatch delivery.
        runCatching { Notify.scheduleStatusChangedRefresh() }
    }
}
