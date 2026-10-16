package tk.glucodata;

/** Executes the legacy timer method with recording publication and refresh surfaces. */
public class OngoingTimerHarness {
    static boolean isWearable, doLog;
    static String LOG_ID = "test", GLUCOSENOTIFICATION = "glucose";
    static class Log { static void i(String tag, String message) {} }
    static class Applic {
        static Applic getContext() { return new Applic(); }
        String getString(int id) { return "No new value "; }
    }
    static class R {
        static class string { static int nonewvalue; }
        static class drawable { static int novalue; }
    }
    java.text.DateFormat timef = new java.text.SimpleDateFormat("HH:mm");
    int refreshRequests, directStalePosts;
    long storedReadingTime;
    String displayed = "fresh";
    void scheduleVisualNotificationRefresh() { ++refreshRequests; }
    void placelargenotification(int icon, String message, String channel, boolean once) { ++directStalePosts; displayed = "stale"; }
    // PRODUCTION_OLD_NOTIFICATION
    void flush(long now) {
        if (refreshRequests == 0) return;
        displayed = storedReadingTime <= 0 ? "waiting"
                : NotificationRefreshPolicy.isFresh(storedReadingTime, now, 180_000) ? "fresh" : "stale";
    }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void run() {
        long now = 1_790_000_000_000L;
        OngoingTimerHarness fresh = new OngoingTimerHarness();
        fresh.storedReadingTime = now;
        fresh.oldnotification(now - 12 * 60_000L);
        check(fresh.directStalePosts == 0 && fresh.refreshRequests == 1, "old timer must not directly overwrite fresh phone notification");
        fresh.flush(now);
        check(fresh.displayed.equals("fresh"), "current reading wins over old timer time");
        OngoingTimerHarness outage = new OngoingTimerHarness();
        outage.storedReadingTime = now - 12 * 60_000L;
        outage.oldnotification(outage.storedReadingTime);
        outage.flush(now);
        check(outage.displayed.equals("stale"), "real outage remains stale under the existing freshness policy");
        outage.storedReadingTime = now;
        outage.flush(now);
        check(outage.displayed.equals("fresh"), "new reading during queued refresh wins");
        OngoingTimerHarness empty = new OngoingTimerHarness();
        empty.oldnotification(now - 12 * 60_000L);
        empty.flush(now);
        check(empty.displayed.equals("waiting"), "no reading must not fabricate a timestamp");
        isWearable = true;
        OngoingTimerHarness wear = new OngoingTimerHarness();
        wear.oldnotification(now);
        check(wear.directStalePosts == 1 && wear.refreshRequests == 0, "Wear timer unchanged");
        isWearable = false;
    }
    public static void main(String[] args) { run(); System.out.println("PASS: actual oldnotification method; fresh phone, outage, arrival during refresh, absent reading and unchanged Wear"); }
}
