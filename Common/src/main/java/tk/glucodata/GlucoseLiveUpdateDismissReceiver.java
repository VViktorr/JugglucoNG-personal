package tk.glucodata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Unpin only the optional presentation; keep sensor monitoring and alarms running. */
public final class GlucoseLiveUpdateDismissReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !GlucoseLiveUpdate.ACTION_UNPIN.equals(intent.getAction())) return;
        context.getSharedPreferences("tk.glucodata_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean(GlucoseLiveUpdate.PREFERENCE, false).apply();
        Notify.scheduleStoredGlucoseRefresh();
    }
}
