package tk.glucodata;

import android.app.Notification;

/**
 * Opt-in OEM compatibility experiment, not a verified OxygenOS/ColorOS fix.
 *
 * Glyph's StatusBarHooks reads this per-notification extra to identify app-icon
 * substitution. That does NOT establish that OplusNotificationFixHelper accepts
 * it as an input: system_server may overwrite it along with smallIcon.
 * Never replace glucose artwork or modify channel/group/alarm policy here.
 */
final class OplusStatusIconRequest {
    static final String PREFERENCE = "notification_oplus_original_status_icon";
    static final String USE_APP_ICON = "oplus_smallicon_use_app_icon";

    private OplusStatusIconRequest() { }

    static void apply(Notification.Builder builder, boolean enabled, int sdk, boolean wearable) {
        if (!enabled || wearable || sdk < 36) return;
        // Merge into existing extras; do not discard accessibility/style metadata.
        builder.getExtras().putBoolean(USE_APP_ICON, false);
    }
}
