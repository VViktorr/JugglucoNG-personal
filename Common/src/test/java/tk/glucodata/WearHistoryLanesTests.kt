package tk.glucodata

import org.junit.Assert.*
import org.junit.Test

class WearHistoryLanesTests {
    @Test fun rawOnlyHistoryRemainsRawOnly() {
        assertTrue(WearHistoryLanes.hasValue(0f, 105f))
        assertEquals(0f, WearHistoryLanes.validMgdl(0f), 0f)
        assertEquals(105f, WearHistoryLanes.validMgdl(105f), 0f)
    }

    @Test fun autoOnlyAndCompleteHistoryAreAccepted() {
        assertTrue(WearHistoryLanes.hasValue(110f, 0f))
        assertTrue(WearHistoryLanes.hasValue(110f, 105f))
    }

    @Test fun invalidLaneDoesNotHideTheValidOtherLane() {
        assertTrue(WearHistoryLanes.hasValue(Float.NaN, 105f))
        assertTrue(WearHistoryLanes.hasValue(11557f, 105f))
        assertTrue(WearHistoryLanes.hasValue(110f, Float.POSITIVE_INFINITY))
        assertEquals(0f, WearHistoryLanes.validMgdl(11557f), 0f)
    }

    @Test fun emptyAndDiagnosticOnlyHistoryAreRejected() {
        assertFalse(WearHistoryLanes.hasValue(0f, 0f))
        assertFalse(WearHistoryLanes.hasValue(Float.NaN, Float.NaN))
        assertFalse(WearHistoryLanes.hasValue(11557f, -1f))
    }
}
