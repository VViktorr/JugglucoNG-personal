package tk.glucodata;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.RequiresApi;
import java.util.Locale;

/** Optional replacement for the custom chart notification; never used for alarms or Wear. */
final class GlucoseLiveUpdate {
    static final String PREFERENCE = "notification_glucose_live_update";
    static final String ACTION_UNPIN = "tk.glucodata.action.UNPIN_GLUCOSE_LIVE_UPDATE";

    private GlucoseLiveUpdate() { }

    @RequiresApi(36)
    static Notification tryBuild(Context context, Notification.Builder builder,
            float value, boolean mmol, long readingTime) {
        try {
            final String unit = context.getString(mmol ? R.string.mmolL : R.string.mgdL);
            final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            final String shortValue = String.format(locale, mmol ? "%.1f" : "%.0f", value);
            final String label = context.getString(R.string.notification_live_update_glucose);
            final PendingIntent unpin = PendingIntent.getBroadcast(context, 81433,
                    new Intent(context, GlucoseLiveUpdateDismissReceiver.class).setAction(ACTION_UNPIN),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setSmallIcon(R.drawable.novalue)
                    .setContentTitle(label + ": " + shortValue + " " + unit)
                    .setContentText(shortValue + " " + unit)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setWhen(readingTime)
                    .setShowWhen(true)
                    .setColorized(false)
                    .setDeleteIntent(unpin)
                    .addAction(new Notification.Action.Builder(null,
                            context.getString(R.string.notification_live_update_unpin), unpin).build());
            // Do not gate posting on canPostPromotedNotifications(): OEM implementations
            // differ. The user's system promotion/demotion setting remains authoritative.
            // The setter was added in SDK 36.1. The documented extra also lets
            // base-36 OEM builds that support promotion consume the request.
            builder.getExtras().putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true);
            builder.setShortCriticalText(shortValue);
            if (Build.VERSION.SDK_INT >= 37) {
                try {
                    Api37.apply(builder, value, mmol, unit, label);
                } catch (RuntimeException | LinkageError unsupportedMetricStyle) {
                    // An OEM may omit or gate the new template. Keep the API 36 readout.
                    builder.setStyle(null);
                }
            }
            return builder.build();
        } catch (RuntimeException | LinkageError unsupportedLiveUpdate) {
            Log.stack("GlucoseLiveUpdate", "Using existing glucose notification", unsupportedLiveUpdate);
            return null;
        }
    }

    /** Keep API 37 class references out of the API 36 path. */
    @RequiresApi(37)
    private static final class Api37 {
        static void apply(Notification.Builder builder, float value, boolean mmol,
                String unit, String label) {
            final Notification.Metric.MetricValue metricValue = mmol
                    ? new Notification.Metric.FixedFloat(value, unit, 1, 1)
                    : new Notification.Metric.FixedInt(Math.round(value), unit);
            builder.setStyle(new Notification.MetricStyle()
                    .addMetric(new Notification.Metric(metricValue, label))
                    .setCriticalMetric(0));
        }
    }
}
