package tk.glucodata;

import android.app.Notification;

/**
 * Prefix + integer-extra exception observed in the user's OxygenOS framework.
 * An experiment, not a verified device fix and not an Android compatibility API.
 */
final class OplusSmallIconCompatibility {
    static final String EXTRA = "oplus_notification_icon_use_smallicon";

    private OplusSmallIconCompatibility() { }

    static boolean shouldRequest(boolean testBuild, String packageName, int sdk,
            boolean wearable, boolean glucoseChannel) {
        return testBuild && sdk >= 36 && !wearable && glucoseChannel
                && packageName != null
                && (packageName.startsWith("com.oplus.")
                    || packageName.startsWith("com.oppo.")
                    || packageName.startsWith("com.coloros."));
    }

    static void apply(Notification.Builder builder, boolean testBuild, String packageName,
            int sdk, boolean wearable, boolean glucoseChannel) {
        if (shouldRequest(testBuild, packageName, sdk, wearable, glucoseChannel)) {
            // The framework calls Bundle.getInt(key, 0) == 1, NOT getBoolean.
            // Merge without changing smallIcon, channels, grouping or other extras.
            builder.getExtras().putInt(EXTRA, 1);
        }
    }
}
