package tk.glucodata

import org.junit.Assert.*
import org.junit.Test

class WidgetCurrentFreshnessTests {
    private fun reading(time: Long): CurrentDisplaySource.Snapshot =
        requireNotNull(CurrentDisplaySource.resolveFromLive(
            liveValueText = null,
            liveNumericValue = 110f,
            rate = 0f,
            targetTimeMillis = time,
            sensorId = "test",
            sensorGen = 1,
            index = 0,
            source = "test",
            recentPoints = emptyList(),
            viewMode = 0,
            isMmol = false
        ))

    @Test fun fifteenMinuteCurrentReadingIsHidden() {
        val stale = reading(System.currentTimeMillis() - 15 * 60_000L)
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(stale, emptyList(), "test"))
    }

    @Test fun staleChartCannotResurrectAMissingCurrentValue() {
        val history = listOf(GlucosePoint(System.currentTimeMillis() - 15 * 60_000L, 110f, 105f))
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(null, history, "test"))
        assertEquals("Chart history is retained", 1, history.size)
    }

    @Test fun freshCurrentReadingStillDisplays() {
        val fresh = reading(System.currentTimeMillis() - 60_000L)
        assertSame(fresh, WidgetDisplaySource.resolveDisplaySnapshot(fresh, emptyList(), "test"))
    }

    @Test fun noReadingAndNoHistoryRemainEmpty() {
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(null, emptyList(), "test"))
    }

    @Test fun justExpiredNumberIsHiddenWhileItsChartHistoryIsRetained() {
        val timestamp = System.currentTimeMillis() - Notify.glucosetimeout - 1_000L
        val stale = reading(timestamp)
        val history = listOf(GlucosePoint(timestamp, 110f, 105f))
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(stale, history, "test"))
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(null, history, "test"))
        assertEquals(timestamp, history.single().timestamp)
    }

    @Test fun numberReturnsAfterTheNextRealReading() {
        val history = listOf(GlucosePoint(System.currentTimeMillis() - 15 * 60_000L, 110f, 105f))
        assertNull(WidgetDisplaySource.resolveDisplaySnapshot(null, history, "test"))
        val fresh = reading(System.currentTimeMillis() - 1_000L)
        assertSame(fresh, WidgetDisplaySource.resolveDisplaySnapshot(fresh, history, "test"))
    }
}
