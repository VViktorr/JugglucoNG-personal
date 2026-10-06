package tk.glucodata.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacySensorStatusTests {
    private val startMs = 1_700_000_000_000L

    @Test
    fun libre2WarmupCountsDownFromSixtyMinutes() {
        assertEquals(60, libre2WarmupMinutes(2, startMs, startMs, 60))
        assertEquals(60, libre2WarmupMinutes(2, startMs, startMs + 59_000L, 60))
        assertEquals(59, libre2WarmupMinutes(2, startMs, startMs + 60_000L, 60))
        assertEquals(1, libre2WarmupMinutes(2, startMs, startMs + 59L * 60_000L, 60))
    }

    @Test
    fun libre2WarmupStopsAtSixtyMinutes() {
        assertNull(libre2WarmupMinutes(2, startMs, startMs + 60L * 60_000L, 60))
    }

    @Test
    fun warmupCountdownDoesNotApplyToOtherSensors() {
        assertNull(libre2WarmupMinutes(3, startMs, startMs, 60))
        assertNull(libre2WarmupMinutes(0x40, startMs, startMs, 30))
    }

    @Test
    fun invalidWarmupMetadataDoesNotCreateCountdown() {
        assertNull(libre2WarmupMinutes(2, 0L, startMs, 60))
        assertNull(libre2WarmupMinutes(2, startMs, startMs, 0))
        assertNull(libre2WarmupMinutes(2, startMs, startMs - 1L, 60))
    }
}
