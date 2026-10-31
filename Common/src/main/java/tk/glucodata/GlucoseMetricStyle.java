package tk.glucodata;

import android.app.Notification;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

/** Applies Android 17's metric notification template without requesting Live Update promotion. */
public final class GlucoseMetricStyle {
    public static final String PREFERENCE = "notification_glucose_metric_style";

    private GlucoseMetricStyle() { }

    public static boolean apply(Notification.Builder builder, Context context, float value, boolean mmol) {
        if (Build.VERSION.SDK_INT < 37 || builder == null || !Float.isFinite(value) || value <= 0f) {
            return false;
        }
        try {
            final String unit = context.getString(mmol ? R.string.mmolL : R.string.mgdL);
            final String label = context.getString(R.string.notification_metric_style_glucose);
            Api37.apply(builder, value, mmol, unit, label);
            return true;
        } catch (RuntimeException | LinkageError unsupportedMetricStyle) {
            Log.stack("GlucoseMetricStyle", "Using the standard glucose notification", unsupportedMetricStyle);
            return false;
        }
    }

    /** Keep API 37 references out of the pre-Android 17 execution path. */
    @RequiresApi(37)
    private static final class Api37 {
        static void apply(Notification.Builder builder, float value, boolean mmol, String unit, String label) {
            final Notification.Metric.MetricValue metricValue = mmol
                    ? new Notification.Metric.FixedFloat(value, unit, 1, 1)
                    : new Notification.Metric.FixedInt(Math.round(value), unit);
            builder.setStyle(new Notification.MetricStyle()
                    .addMetric(new Notification.Metric(metricValue, label))
                    .setCriticalMetric(0));
        }
    }
}
