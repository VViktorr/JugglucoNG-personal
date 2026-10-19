package tk.glucodata;

/** Eligibility for numeric Live Updates, independent of Android framework classes. */
final class GlucoseLiveUpdatePolicy {
    private GlucoseLiveUpdatePolicy() { }

    static boolean canUse(boolean enabled, int sdk, boolean wearable, boolean glucoseChannel,
            float value, long readingTime, long now, long freshness) {
        return enabled && sdk >= 36 && !wearable && glucoseChannel
                && Float.isFinite(value) && value > 0f
                && readingTime > 0L && freshness > 0L
                && readingTime <= now && now - readingTime <= freshness;
    }
}
