package tk.glucodata;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.RequiresApi;

/** Optional phone presentation applied after the normal notification lifecycle has rendered. */
public final class SystemGlucoseNotification {
    // New keys intentionally do not reuse preferences from the older personal implementation.
    public static final String LIVE_UPDATE = "notification_system_live_update_v1";
    public static final String METRIC_STYLE = "notification_system_metric_style_v1";
    private SystemGlucoseNotification() { }

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE);
    }

    public static void setEnabled(Context context, String key, boolean enabled) {
        if (!LIVE_UPDATE.equals(key) && !METRIC_STYLE.equals(key)) throw new IllegalArgumentException("key");
        preferences(context).edit().putBoolean(key, enabled).apply();
        Notify.forgetPublishedOngoingContent();
        Notify.scheduleStatusChangedRefresh();
    }

    static Notification present(Context context, Notification original,
            CurrentDisplaySource.Snapshot reading, float rate) {
        if (Applic.isWearable || Build.VERSION.SDK_INT < 36 || reading == null) return original;
        final long now = System.currentTimeMillis();
        final long age = now - reading.getTimeMillis();
        if (reading.getTimeMillis() <= 0L || age < 0L || age > Notify.glucosetimeout
                || !Float.isFinite(reading.getPrimaryValue()) || reading.getPrimaryValue() <= 0f)
            return original;
        SharedPreferences prefs = preferences(context);
        final boolean live = prefs.getBoolean(LIVE_UPDATE, false);
        final boolean metric = Build.VERSION.SDK_INT >= 37 && prefs.getBoolean(METRIC_STYLE, false);
        if (!live && !metric) return original;
        try {
            return Api36.render(context, original, reading, rate, live, metric);
        } catch (RuntimeException | LinkageError error) {
            Log.stack("SystemGlucoseNotification", "system presentation unavailable", error);
            return original;
        }
    }

    @RequiresApi(36)
    private static final class Api36 {
        static Notification render(Context context, Notification original,
                CurrentDisplaySource.Snapshot reading, float rate, boolean live, boolean metric) {
            final String value = reading.getPrimaryStr();
            final String unit = context.getString(reading.isMmol() ? R.string.mmolL : R.string.mgdL);
            final String label = context.getString(R.string.system_glucose_reading_label);
            final String text = GlucoseNotificationText.title(value,
                    java.util.Collections.emptyList(), rate) + " " + unit;
            // Recovering preserves the foreground ID's channel, actions and announcement policy.
            // RemoteViews are incompatible with promotion and must not survive this conversion.
            final Notification.Builder builder = Notification.Builder.recoverBuilder(context, original)
                    .setCustomContentView(null).setCustomBigContentView(null).setCustomHeadsUpContentView(null)
                    .setGroup(null).setGroupSummary(false).setColorized(false)
                    .setOngoing(true).setAutoCancel(false).setCategory(Notification.CATEGORY_STATUS)
                    .setSubText(null).setContentInfo(null)
                    .setWhen(reading.getTimeMillis()).setShowWhen(true);
            applyStandardContent(builder, label, text);
            builder.getExtras().putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, live);
            if (live) {
                builder.setShortCriticalText(value);
                // The original small icon contains a painted glucose number.
                // A promoted chip would show it alongside the same number as system text.
                if (preferences(context).getBoolean("notification_hide_status_icon", false)) {
                    builder.setSmallIcon(R.drawable.transparent_icon);
                } else if (Float.isFinite(rate)) {
                    builder.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(
                            NotificationChartDrawer.drawArrow(context, rate, reading.isMmol(),
                                    android.graphics.Color.WHITE, 1.0f, false, false)));
                } else {
                    builder.setSmallIcon(R.drawable.novalue);
                }
            }
            if (metric) {
                try {
                    // MetricStyle supplies the number and unit itself; don't repeat them in the title.
                    builder.setContentTitle(GlucoseNotificationText.title(label,
                            java.util.Collections.emptyList(), rate)).setContentText(null);
                    Api37.apply(builder, reading, unit,
                            context.getString(R.string.system_glucose_current_reading));
                    return builder.build();
                } catch (RuntimeException | LinkageError unavailable) {
                    // A device missing MetricStyle can still show the independently requested Live Update.
                    if (!live) return original;
                    applyStandardContent(builder, label, text);
                }
            }
            return builder.build();
        }

        private static void applyStandardContent(Notification.Builder builder, String label, String text) {
            // Show the reading once, with a short, correctly translated description below it.
            builder.setContentTitle(text).setContentText(label)
                    .setStyle(new Notification.BigTextStyle().bigText(label));
        }
    }

    @RequiresApi(37)
    private static final class Api37 {
        static void apply(Notification.Builder builder, CurrentDisplaySource.Snapshot reading,
                String unit, String label) {
            Notification.Metric.MetricValue number = reading.isMmol()
                    ? new Notification.Metric.FixedFloat(reading.getPrimaryValue(), unit, 1, 1)
                    : new Notification.Metric.FixedInt(Math.round(reading.getPrimaryValue()), unit);
            builder.setStyle(new Notification.MetricStyle()
                    .addMetric(new Notification.Metric(number, label)).setCriticalMetric(0));
        }
    }
}
