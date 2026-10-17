package tk.glucodata;

/** Executes the legacy timer method with recording publication and refresh surfaces. */
public class OngoingTimerHarness {
    static boolean isWearable, doLog;
    static String LOG_ID = "test", GLUCOSENOTIFICATION = "glucose";
    static class Log { static void i(String tag, String message) {} }
    static class Applic {
        static final Applic app = new Applic();
        static Applic getContext() { return new Applic(); }
        String getString(int id) { return "No new value "; }
    }
    static class GlucoseUpdateBroadcaster {
        static int sends;
        static void send(Applic context) { ++sends; }
    }
    static class R {
        static class string { static int nonewvalue; }
        static class drawable { static int novalue; }
    }
    java.text.DateFormat timef = new java.text.SimpleDateFormat("HH:mm");
    int refreshRequests, directStalePosts;
    static final Object foregroundPublicationLock = new Object();
    static class keeprunning { static final Object theservice = new Object(); }
    long freshnessDeadlineGeneration = 1L, foregroundVisualGeneration = 1L;
    Object freshnessDeadlineService = keeprunning.theservice;
    long storedReadingTime;
    String displayed = "fresh";
    void scheduleVisualNotificationRefresh() { ++refreshRequests; }
    void placelargenotification(int icon, String message, String channel, boolean once) { ++directStalePosts; displayed = "stale"; }
    // PRODUCTION_OLD_NOTIFICATION
    // PRODUCTION_FRESHNESS_DEADLINE
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
        OngoingTimerHarness deadline = new OngoingTimerHarness();
        deadline.freshnessDeadlineRefreshRunnable.run();
        check(deadline.refreshRequests == 1 && GlucoseUpdateBroadcaster.sends == 1,
                "expiration without any new reading must refresh AOD and all widgets");
        deadline.freshnessDeadlineRefreshRunnable.run();
        check(GlucoseUpdateBroadcaster.sends == 1, "a consumed expiry must not repeat");
        OngoingTimerHarness superseded = new OngoingTimerHarness();
        superseded.foregroundVisualGeneration = 2L;
        superseded.freshnessDeadlineRefreshRunnable.run();
        check(superseded.refreshRequests == 0 && GlucoseUpdateBroadcaster.sends == 1,
                "an older reading's expiry must not overwrite a newer publication");
        OngoingTimerHarness replacedService = new OngoingTimerHarness();
        replacedService.freshnessDeadlineService = new Object();
        replacedService.freshnessDeadlineRefreshRunnable.run();
        check(replacedService.refreshRequests == 0 && GlucoseUpdateBroadcaster.sends == 1,
                "an old service's expiry must not invalidate the replacement");
        GlucoseUpdateBroadcaster.sends = 0;
        OngoingTimerHarness fresh = new OngoingTimerHarness();
        fresh.storedReadingTime = now;
        fresh.oldnotification(now - 12 * 60_000L);
        check(fresh.directStalePosts == 0 && fresh.refreshRequests == 1, "old timer must not directly overwrite fresh phone notification");
        check(GlucoseUpdateBroadcaster.sends == 1, "wake-up timer must invalidate widgets even without new glucose");
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
        int phoneWidgetUpdates = GlucoseUpdateBroadcaster.sends;
        OngoingTimerHarness wear = new OngoingTimerHarness();
        wear.oldnotification(now);
        check(wear.directStalePosts == 1 && wear.refreshRequests == 0, "Wear timer unchanged");
        check(GlucoseUpdateBroadcaster.sends == phoneWidgetUpdates, "Wear must not invalidate phone widgets");
        isWearable = false;
    }
    public static void main(String[] args) { run(); System.out.println("PASS: actual oldnotification method; fresh phone, outage, arrival during refresh, absent reading and unchanged Wear"); }
}
