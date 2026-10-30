package tk.glucodata;

import org.junit.Test;
import static org.junit.Assert.*;

public class GlucoseLiveUpdatePolicyTests {
    private static final long NOW = 1_000_000L;
    private static final long FRESHNESS = 330_000L;

    private boolean eligible(boolean enabled, int sdk, boolean wear, boolean glucose,
            float value, long time) {
        return GlucoseLiveUpdatePolicy.canUse(enabled, sdk, wear, glucose,
                value, time, NOW, FRESHNESS);
    }

    @Test public void requiresExplicitOptInAndAndroid16OrNewer() {
        assertFalse(eligible(false, 37, false, true, 105f, NOW));
        assertFalse(eligible(true, 35, false, true, 105f, NOW));
        assertTrue(eligible(true, 36, false, true, 105f, NOW));
        assertTrue(eligible(true, 37, false, true, 5.8f, NOW));
    }

    @Test public void neverChangesWearOrAlarmNotifications() {
        assertFalse(eligible(true, 37, true, true, 105f, NOW));
        assertFalse(eligible(true, 37, false, false, 105f, NOW));
    }

    @Test public void acceptsExactFreshnessBoundaryButRejectsExpiredReading() {
        assertTrue(eligible(true, 36, false, true, 105f, NOW - FRESHNESS));
        assertFalse(eligible(true, 36, false, true, 105f, NOW - FRESHNESS - 1L));
    }

    @Test public void missingOrFutureReadingCannotBecomeCurrentChip() {
        assertFalse(eligible(true, 37, false, true, 105f, 0L));
        assertFalse(eligible(true, 37, false, true, 105f, -1L));
        assertFalse(eligible(true, 37, false, true, 105f, NOW + 1L));
    }

    @Test public void invalidNumericValuesCannotReachSystemMetric() {
        for (float value : new float[] { Float.NaN, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, 0f, -1f }) {
            assertFalse(eligible(true, 37, false, true, value, NOW));
        }
    }

    @Test public void invalidFreshnessDoesNotPermitPromotion() {
        assertFalse(GlucoseLiveUpdatePolicy.canUse(true, 37, false, true, 105f, NOW, NOW, 0L));
        assertFalse(GlucoseLiveUpdatePolicy.canUse(true, 37, false, true, 105f, NOW, NOW, -1L));
    }
}
