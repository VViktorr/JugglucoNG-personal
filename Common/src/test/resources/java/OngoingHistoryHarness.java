package tk.glucodata;

import java.util.*;

/** Executes the actual ordinary-notification history selector with recording sources. */
public class OngoingHistoryHarness {
    static boolean isWearable, doLog;
    static String LOG_ID = "test";
    static long glucosetimeout = 180_000L;
    static class Applic { static int unit; }
    static class Log {
        static void i(String tag, String message) {}
        static void stack(String tag, String message, Throwable t) {}
    }
    static class DisplayTrendSource { static long TREND_WINDOW_MS = 20 * 60_000L; }
    static class GlucosePoint {
        long timestamp; float value, rawValue;
        GlucosePoint(long t, float a, float r) { timestamp=t; value=a; rawValue=r; }
    }
    static class DisplayValueResolver {
        static class Values { float value; Values(float value) { this.value=value; } float getPrimaryValue() { return value; } }
        static Values resolve(float auto, float raw, int mode, boolean mmol) {
            return new Values(mode == 1 || mode == 3 ? raw > 0 ? raw : auto : auto > 0 ? auto : Float.NaN);
        }
    }
    static class LiveReadingLanes {
        float auto, raw;
        static LiveReadingLanes stock(float a, float r) { LiveReadingLanes l = new LiveReadingLanes(); l.auto=a; l.raw=r; return l; }
    }
    static class CurrentDisplaySource {
        static int mode;
        static boolean keptIncoming;
        static class Snapshot {
            long time; float primary; int gen=2, index=1;
            Snapshot(long t, float p) { time=t; primary=p; }
            long getTimeMillis() { return time; }
            float getPrimaryValue() { return primary; }
            int getSensorGen() { return gen; }
            int getIndex() { return index; }
        }
        static int resolveViewModeForSensor(String sensor) { return mode; }
        static Snapshot resolveIncomingReading(LiveReadingLanes lanes, float rate, long time,
                String sensor, int gen, int index, String source, long window, boolean preferIncoming) {
            keptIncoming = preferIncoming;
            return new Snapshot(time, DisplayValueResolver.resolve(lanes.auto, lanes.raw, mode, Applic.unit == 1).getPrimaryValue());
        }
    }
    static class NotificationHistorySource {
        static Map<String, List<GlucosePoint>> sources = new HashMap<>();
        static boolean broken;
        static List<GlucosePoint> getDisplayHistory(long start, boolean mmol, String sensor) {
            if (broken) throw new IllegalStateException("Room unavailable");
            return sources.getOrDefault(sensor, Collections.emptyList());
        }
    }
    static Map<String, CurrentDisplaySource.Snapshot> live = new HashMap<>();
    static CurrentDisplaySource.Snapshot resolveNotificationCurrentSnapshot(String sensor) { return live.get(sensor); }
    // PRODUCTION_ONGOING_HISTORY
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void run() {
        long now = System.currentTimeMillis();
        CurrentDisplaySource.Snapshot ble = new CurrentDisplaySource.Snapshot(now - 120_000, 100f);
        live.put("main", ble);
        NotificationHistorySource.sources.put("main", Arrays.asList(
                new GlucosePoint(now - 90_000, 108f, 91f), new GlucosePoint(now - 60_000, 110f, 95f)));
        CurrentDisplaySource.Snapshot result = resolveOngoingNotificationCurrentSnapshot("main");
        check(result.getTimeMillis() == now - 60_000 && result.getPrimaryValue() == 110f, "newest NFC history must beat older fresh BLE");
        check(CurrentDisplaySource.keptIncoming, "smoothing/thinning must retain selected sample timestamp");
        check(resolveNotificationCurrentSnapshot("main") == ble, "original resolver used by alarms stays unchanged");
        live.put("main", new CurrentDisplaySource.Snapshot(now - 30_000, 115f));
        check(resolveOngoingNotificationCurrentSnapshot("main") == live.get("main"), "newer BLE wins");
        live.remove("main");
        NotificationHistorySource.sources.put("main", Arrays.asList(new GlucosePoint(now - 12 * 60_000, 110f, 95f)));
        check(resolveOngoingNotificationCurrentSnapshot("main") == null, "old NFC cannot become current");
        live.put("main", ble);
        NotificationHistorySource.sources.put("secondary", Arrays.asList(new GlucosePoint(now - 1_000, 250f, 240f)));
        check(resolveOngoingNotificationCurrentSnapshot("main") == ble, "secondary history cannot replace main");
        NotificationHistorySource.sources.put("main", Arrays.asList(new GlucosePoint(now - 60_000, 0f, 95f)));
        for (int mode : new int[]{0,2}) {
            CurrentDisplaySource.mode = mode;
            check(resolveOngoingNotificationCurrentSnapshot("main") == ble, "Raw-only does not invent Auto");
        }
        for (int mode : new int[]{1,3}) {
            CurrentDisplaySource.mode = mode;
            check(resolveOngoingNotificationCurrentSnapshot("main").getPrimaryValue() == 95f, "Raw-primary uses real Raw");
        }
        CurrentDisplaySource.mode = 0;
        NotificationHistorySource.broken = true;
        check(resolveOngoingNotificationCurrentSnapshot("main") == ble, "failed history query retains BLE");
        NotificationHistorySource.broken = false;
        isWearable = true;
        check(resolveOngoingNotificationCurrentSnapshot("main") == ble, "Wear unchanged");
        isWearable = false;
    }
    public static void main(String[] args) { run(); System.out.println("PASS: production ordinary-notification selector; newer NFC/BLE, stale history, main sensor, Auto/Raw, history failure and unchanged alarm resolver/Wear"); }
}
