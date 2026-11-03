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
    public static final String SHOW_TREND = "notification_system_show_trend_v1";
    public static final String TEXT_TREND = "notification_system_text_trend_v1";
    public static final String TEXT_UNIT = "notification_system_text_unit_v1";
    public static final String SHOW_TIME = "notification_system_show_time_v1";
    public static final String METRIC_HEADER = "notification_system_metric_header_v1";
    public static final String METRIC_CRITICAL = "notification_system_metric_critical_v1";
    public static final String ICON_MODE = "notification_system_icon_mode_v1";
    public static final String TEXT_ORDER = "notification_system_text_order_v1";
    private SystemGlucoseNotification() { }

    public static void setIntOption(Context context, String key, int value) {
        int maximum = ICON_MODE.equals(key) ? 2 : TEXT_ORDER.equals(key) ? 1 : -1;
        if (value < 0 || value > maximum) throw new IllegalArgumentException("option");
        preferences(context).edit().putInt(key, value).apply();
        refreshPresentation();
    }

    private static void refreshPresentation() {
        Notify.forgetPublishedOngoingContent();
        Notify.scheduleStatusChangedRefresh();
    }

    public static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE);
    }

    public static void setEnabled(Context context, String key, boolean enabled) {
        if (!LIVE_UPDATE.equals(key) && !METRIC_STYLE.equals(key)
                && !SHOW_TREND.equals(key) && !TEXT_TREND.equals(key) && !TEXT_UNIT.equals(key)
                && !SHOW_TIME.equals(key) && !METRIC_CRITICAL.equals(key) && !METRIC_HEADER.equals(key))
            throw new IllegalArgumentException("key");
        preferences(context).edit().putBoolean(key, enabled).apply();
        refreshPresentation();
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
            final SharedPreferences prefs = preferences(context);
            final boolean showTrend = prefs.getBoolean(SHOW_TREND, true);
            final float displayRate = showTrend ? rate : Float.NaN;
            final boolean criticalMetric = prefs.getBoolean(METRIC_CRITICAL, true);
            final String value = reading.getPrimaryStr();
            final String unit = context.getString(reading.isMmol() ? R.string.mmolL : R.string.mgdL);
            final String label = context.getString(R.string.system_glucose_reading_label);
            final String text = GlucoseNotificationText.title(value,
                    java.util.Collections.emptyList(), displayRate) + " " + unit;
            // Recovering preserves the foreground ID's channel, actions and announcement policy.
            // RemoteViews are incompatible with promotion and must not survive this conversion.
            final Notification.Builder builder = Notification.Builder.recoverBuilder(context, original)
                    .setCustomContentView(null).setCustomBigContentView(null).setCustomHeadsUpContentView(null)
                    .setGroup(null).setGroupSummary(false).setColorized(false)
                    .setOngoing(true).setAutoCancel(false).setCategory(Notification.CATEGORY_STATUS)
                    .setSubText(null).setContentInfo(null)
                    .setWhen(reading.getTimeMillis()).setShowWhen(prefs.getBoolean(SHOW_TIME, true));
            applyStandardContent(builder, label, text);
            builder.getExtras().putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, live);
            if (live) {
                String chipText = value;
                if (showTrend && prefs.getBoolean(TEXT_TREND, false)) {
                    String arrow = ExchangeTrend.fromRate(rate).arrow;
                    if (!arrow.isEmpty()) {
                        chipText = prefs.getInt(TEXT_ORDER, 0) == 1
                                ? arrow + " " + value : value + " " + arrow;
                    }
                }
                if (prefs.getBoolean(TEXT_UNIT, false)) chipText += " " + unit;
                builder.setShortCriticalText(chipText);
                // The original small icon contains a painted glucose number.
                // A promoted chip would show it alongside the same number as system text.
                int iconMode = prefs.contains(ICON_MODE) ? prefs.getInt(ICON_MODE, 0)
                        : prefs.getBoolean("notification_hide_status_icon", false) ? 2 : 0;
                if (iconMode == 2) {
                    builder.setSmallIcon(R.drawable.transparent_icon);
                } else if (iconMode == 0 && showTrend && Float.isFinite(rate)) {
                    builder.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(
                            NotificationChartDrawer.drawArrow(context, rate, reading.isMmol(),
                                    android.graphics.Color.WHITE, 1.0f, false, false)));
                } else {
                    builder.setSmallIcon(R.drawable.novalue);
                }
            }
            if (metric) {
                try {
                    // Keep a real title for promotion/accessibility and never repeat the number.
                    final String metricText = GlucoseNotificationText.title(value,
                            java.util.Collections.emptyList(), displayRate);
                    final boolean headerLayout = prefs.getBoolean(METRIC_HEADER, true);
                    final String arrow = ExchangeTrend.fromRate(displayRate).arrow;
                    // Use the title slot that this device renders larger for the number,
                    // and the native metric slot for the arrow, without duplicate numbers.
                    final boolean splitHeader = headerLayout && !arrow.isEmpty();
                    builder.setContentTitle(splitHeader ? value : context.getString(R.string.app_name))
                            .setContentText(null).setSubText(null).setContentInfo(null).setShowWhen(false);
                    Api37.apply(builder, splitHeader ? arrow : metricText,
                            !headerLayout && criticalMetric);
                    return builder.build();
                } catch (RuntimeException | LinkageError unavailable) {
                    // A device missing MetricStyle can still show the independently requested Live Update.
                    if (!live) return original;
                    applyStandardContent(builder, label, text);
                    builder.setShowWhen(prefs.getBoolean(SHOW_TIME, true));
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
        static void apply(Notification.Builder builder, String metricText, boolean criticalMetric) {
            // FixedText keeps the display-formatted value and arrow together in
            // the main metric field. No label, unit or arbitrary font-size span.
            builder.setStyle(new Notification.MetricStyle()
                    .addMetric(new Notification.Metric(new Notification.Metric.FixedText(metricText), ""))
                    .setCriticalMetric(criticalMetric ? 0 : Notification.MetricStyle.METRIC_INDEX_NONE));
        }
    }
}
