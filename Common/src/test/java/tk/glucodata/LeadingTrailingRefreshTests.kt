package tk.glucodata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingTrailingRefreshTests {
    private class Harness(interval: Long = 1_000L) {
        var now = 0L
        val delivered = mutableListOf<Long>()
        data class Task(val due: Long, val run: () -> Unit, var cancelled: Boolean = false)
        val tasks = mutableListOf<Task>()
        val refresh = LeadingTrailingRefresh(interval, { now }, { delay, callback ->
            val task = Task(now + delay, callback)
            tasks += task
            LeadingTrailingRefresh.Cancellation { task.cancelled = true }
        }, { delivered += now })

        fun advance(to: Long) {
            while (true) {
                val task = tasks.filter { !it.cancelled && it.due <= to }.minByOrNull { it.due } ?: break
                tasks.remove(task)
                now = task.due
                task.run()
            }
            now = to
        }
    }

    @Test fun burstDeliversImmediatelyAndAtTheTrailingDeadline() {
        val h = Harness()
        h.refresh.request()
        for (at in listOf(100L, 300L, 900L)) {
            h.advance(at)
            h.refresh.request()
        }
        assertEquals(listOf(0L), h.delivered)
        assertEquals(1, h.tasks.count { !it.cancelled })
        h.advance(1_000L)
        assertEquals(listOf(0L, 1_000L), h.delivered)
    }

    @Test fun quietPeriodDoesNotCausePolling() {
        val h = Harness()
        h.refresh.request()
        h.advance(60_000L)
        assertEquals(listOf(0L), h.delivered)
        assertTrue(h.tasks.isEmpty())
    }

    @Test fun finalChunkCancelsTrailingWorkIncludingDispatchedCallbacks() {
        val h = Harness()
        h.refresh.request()
        h.advance(100L)
        h.refresh.request()
        val stale = h.tasks.single().run
        h.advance(500L)
        h.refresh.request(force = true)
        h.advance(1_000L)
        stale()
        assertEquals(listOf(0L, 500L), h.delivered)
    }

    @Test fun firstRequestAfterSleepIsImmediate() {
        val h = Harness(20_000L)
        h.refresh.request()
        h.advance(100_000L)
        h.refresh.request()
        assertEquals(listOf(0L, 100_000L), h.delivered)
    }

    @Test fun lateCancelledCallbackCannotDuplicateANewerUpdate() {
        val h = Harness()
        h.refresh.request()
        h.now = 100L
        h.refresh.request()
        val late = h.tasks.single().run
        // Simulate a busy scheduler: the pending callback has not run at its deadline.
        h.now = 2_000L
        h.refresh.request()
        late()
        assertEquals(listOf(0L, 2_000L), h.delivered)
    }

    @Test fun externalDispatchDoesNotHoldTheRefreshMonitor() {
        lateinit var refresh: LeadingTrailingRefresh
        refresh = LeadingTrailingRefresh(1_000L, { 0L }, { _, _ ->
            LeadingTrailingRefresh.Cancellation { }
        }, { assertFalse(Thread.holdsLock(refresh)) })
        refresh.request()
    }
}
