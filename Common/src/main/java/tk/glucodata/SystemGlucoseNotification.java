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
    public static final String CAPSULE_LAYOUT = "notification_system_capsule_layout_v1";
    public static final String METRIC_CAPSULE_LAYOUT = "notification_system_metric_capsule_layout_v1";
    public static final int ARROW_THEN_VALUE = 0;
    public static final int VALUE_THEN_ARROW = 1;
    public static final String FIELD_VALUE = "value";
    public static final String FIELD_TREND = "trend";
    public static final String FIELD_UNIT = "unit";
    public static final String FIELD_TIME = "time";
    public static final String FIELD_LABEL = "label";
    public static final int POSITION_HEADER = 0;
    public static final int POSITION_MAIN = 1;
    public static final int POSITION_CAPTION = 2;

    private static void validateContentField(String field) {
        if (!FIELD_VALUE.equals(field) && !FIELD_TREND.equals(field) && !FIELD_UNIT.equals(field)
                && !FIELD_TIME.equals(field) && !FIELD_LABEL.equals(field))
            throw new IllegalArgumentException("field");
    }

    // The boolean parameter is retained for source compatibility; both templates share Live Update keys.
    public static String contentVisibleKey(boolean metric, String field) {
        validateContentField(field);
        return "notification_system_live_content_" + field + "_visible_v1";
    }

    public static String contentPositionKey(boolean metric, String field) {
        validateContentField(field);
        return "notification_system_" + "live" + "_content_" + field + "_position_v1";
    }

    public static boolean defaultContentVisible(boolean metric, String field) {
        validateContentField(field);
        return !FIELD_TIME.equals(field);
    }

    public static int defaultContentPosition(boolean metric, String field) {
        validateContentField(field);
        return FIELD_TIME.equals(field) || FIELD_LABEL.equals(field) ? POSITION_CAPTION : POSITION_HEADER;
    }

    public static void setContentVisible(Context context, boolean metric, String field, boolean visible) {
        preferences(context).edit().putBoolean(contentVisibleKey(metric, field), visible).apply();
        refreshPresentation();
    }

    public static void setContentPosition(Context context, boolean metric, String field, int position) {
        if (position < POSITION_HEADER || position > POSITION_CAPTION)
            throw new IllegalArgumentException("position");
        preferences(context).edit().putInt(contentPositionKey(metric, field), position).apply();
        refreshPresentation();
    }

    private static final class ContentSlots {
        final String header, main, caption;
        ContentSlots(String header, String main, String caption) {
            this.header = header;
            this.main = main;
            this.caption = caption;
        }
    }

    private static ContentSlots notificationContent(Context context, SharedPreferences prefs,
            boolean metric, CurrentDisplaySource.Snapshot reading, float rate) {
        String[] fields = {FIELD_LABEL, FIELD_VALUE, FIELD_TREND, FIELD_UNIT, FIELD_TIME};
        String[] content = {
                context.getString(R.string.system_glucose_reading_label),
                reading.getPrimaryStr(),
                ExchangeTrend.fromDisplayRate(rate).arrow,
                context.getString(reading.isMmol() ? R.string.mmolL : R.string.mgdL),
                android.text.format.DateFormat.getTimeFormat(context).format(new java.util.Date(reading.getTimeMillis()))
        };
        java.util.List<java.util.List<String>> slots = java.util.Arrays.asList(
                new java.util.ArrayList<>(), new java.util.ArrayList<>(), new java.util.ArrayList<>());
        for (int i = 0; i < fields.length; ++i) {
            String field = fields[i];
            if (!prefs.getBoolean(contentVisibleKey(metric, field), defaultContentVisible(metric, field))) continue;
            if (content[i] == null || content[i].isEmpty()) continue;
            int position = prefs.getInt(contentPositionKey(metric, field), defaultContentPosition(metric, field));
            if (position < POSITION_HEADER || position > POSITION_CAPTION)
                position = defaultContentPosition(metric, field);
            slots.get(position).add(content[i]);
        }
        return new ContentSlots(String.join(" ", slots.get(POSITION_HEADER)),
                String.join(" ", slots.get(POSITION_MAIN)), String.join(" ", slots.get(POSITION_CAPTION)));
    }

    private SystemGlucoseNotification() { }

    public static void setIntOption(Context context, String key, int value) {
        if ((!CAPSULE_LAYOUT.equals(key) && !METRIC_CAPSULE_LAYOUT.equals(key))
                || value < ARROW_THEN_VALUE || value > VALUE_THEN_ARROW)
            throw new IllegalArgumentException("option");
        preferences(context).edit().putInt(key, value).apply();
        refreshPresentation();
    }

    private static void refreshPresentation() {
        Notify.forgetPublishedOngoingContent();
        Notify.scheduleStatusChangedRefresh();
    }

    public static SharedPreferences preferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE);
        // Retain Live Update preferences as the shared source for both templates.
        // Legacy MetricStyle keys are intentionally ignored.
        return prefs;
    }

    public static void setEnabled(Context context, String key, boolean enabled) {
        if (!LIVE_UPDATE.equals(key) && !METRIC_STYLE.equals(key))
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
        final boolean metric = Build.VERSION.SDK_INT >= 37;
        if (!live) return original;
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
            final float displayRate = rate;
            final String value = reading.getPrimaryStr();
            final ContentSlots liveContent = notificationContent(context, prefs, false, reading, displayRate);
            // Recovering preserves the foreground ID's channel, actions and announcement policy.
            // RemoteViews are incompatible with promotion and must not survive this conversion.
            final Notification.Builder builder = Notification.Builder.recoverBuilder(context, original)
                    .setCustomContentView(null).setCustomBigContentView(null).setCustomHeadsUpContentView(null)
                    .setGroup(null).setGroupSummary(false).setColorized(false)
                    .setOngoing(true).setAutoCancel(false).setCategory(Notification.CATEGORY_STATUS)
                    .setSubText(null).setContentInfo(null)
                    .setWhen(reading.getTimeMillis()).setShowWhen(false);
            applyStandardContent(context, builder, liveContent);
            builder.getExtras().putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, live);
            if (live) {
                applyCapsule(context, builder, value, rate,
                        prefs.getInt(CAPSULE_LAYOUT, ARROW_THEN_VALUE));
            }
            if (metric) {
                try {
                    final ContentSlots metricContent = liveContent;
                    builder.setContentTitle(metricContent.header.isEmpty()
                                    ? context.getString(R.string.app_name) : metricContent.header)
                            .setContentText(null).setSubText(null).setContentInfo(null).setShowWhen(false);
                    if (live) {
                        applyCapsule(context, builder, value, rate,
                                prefs.getInt(CAPSULE_LAYOUT, ARROW_THEN_VALUE));
                    }
                    // MetricStyle requires a nonblank value and label; a dash denotes
                    // an intentionally empty slot, never a fabricated glucose reading.
                    Api37.apply(builder, metricContent.main.isEmpty() ? "—" : metricContent.main,
                            metricContent.caption.isEmpty() ? "—" : metricContent.caption);
                    return builder.build();
                } catch (RuntimeException | LinkageError unavailable) {
                    Log.stack("SystemGlucoseNotification", "MetricStyle unavailable; standard fallback", unavailable);
                    // Restore the standard template using the same content and capsule settings.
                    applyStandardContent(context, builder, liveContent);
                    applyCapsule(context, builder, value, rate,
                            prefs.getInt(CAPSULE_LAYOUT, ARROW_THEN_VALUE));
                    builder.setShowWhen(false);
                }
            }
            return builder.build();
        }

        private static void applyCapsule(Context context, Notification.Builder builder,
                String value, float rate, int layout) {
            final String arrow = ExchangeTrend.fromDisplayRate(rate).arrow;
            final boolean valueIcon = layout == VALUE_THEN_ARROW;
            if (valueIcon) {
                // A single number painted as the icon, with only the arrow as system text.
                // Reuse the regular status icon renderer so locale and font preferences agree.
                builder.setSmallIcon(new StatusIcon(context).getIcon(value));
                builder.setShortCriticalText(arrow.isEmpty() ? "—" : arrow);
            } else {
                builder.setShortCriticalText(value);
                if (!arrow.isEmpty()) {
                    // Use the same seven-state glyph as the text and MetricStyle label.
                    // A continuous-angle renderer can disagree with a flat textual arrow.
                    builder.setSmallIcon(new StatusIcon(context).getIcon(arrow));
                } else {
                    builder.setSmallIcon(R.drawable.novalue);
                }
            }
        }

        private static void applyStandardContent(Context context, Notification.Builder builder, ContentSlots content) {
            String title = content.header.isEmpty() ? context.getString(R.string.app_name) : content.header;
            boolean mainPresent = !content.main.isEmpty();
            String body = mainPresent ? content.main : content.caption;
            String summary = mainPresent && !content.caption.isEmpty() ? content.caption : null;
            builder.setContentTitle(title).setContentText(body.isEmpty() ? null : body)
                    .setStyle(new Notification.BigTextStyle().bigText(body).setSummaryText(summary));
        }
    }

    @RequiresApi(37)
    private static final class Api37 {
        static void apply(Notification.Builder builder, String value, String caption) {
            // Both strings are resolved from the shared Live Update settings.
            // Keep critical metric disabled so capsule settings remain independent.
            builder.setStyle(new Notification.MetricStyle()
                    .addMetric(new Notification.Metric(new Notification.Metric.FixedText(value), caption))
                    .setCriticalMetric(Notification.MetricStyle.METRIC_INDEX_NONE));
        }
    }
}
