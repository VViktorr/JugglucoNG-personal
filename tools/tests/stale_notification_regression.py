from pathlib import Path
import subprocess, tempfile, sys
root = Path(sys.argv[1])
src = (root / "Common/src/main/java/tk/glucodata/Notify.java").read_text()
start = src.index("    private Notification makeStaleReadingNotification(")
opening = src.index("{", start)
depth, end = 1, opening + 1
while depth:
    depth += (src[end] == "{") - (src[end] == "}")
    end += 1
method = src[start:end]
java = """
class StaleNotificationHarness {
    static final String GLUCOSENOTIFICATION = "glucose";
    static final boolean isWearable = false;
    static final Object app = new Object();
    static class CustomGlucoseNotification {
        static void applyStatus(Notification.Builder b, Object context, CharSequence text) {
            b.n.wrappedStatus = text.toString();
        }
    }
    static final int VISIBILITY_PUBLIC = 1, FLAG_ONGOING_EVENT = 2;
    static class R { static class drawable { static final int novalue = 3; } }
    static class Build {
        static class VERSION { static final int SDK_INT = 36; }
        static class VERSION_CODES { static final int LOLLIPOP = 21; }
    }
    static class CurrentDisplaySource {
        static class Snapshot {
            long time = 1000000L;
            long getTimeMillis() { return time; }
            String getFullFormatted() { return "110 mg/dL"; }
            String getPrimaryStr() { return "110"; }
        }
    }
    static class Notification {
        static final int CATEGORY_SERVICE = 4, PRIORITY_DEFAULT = 0;
        int flags, icon, visibility;
        long time;
        String title, text, wrappedStatus;
        boolean silent, ongoing;
        static class Builder {
            Notification n = new Notification();
            Builder setSmallIcon(int x) { n.icon=x; return this; }
            Builder setOnlyAlertOnce(boolean x) { n.silent=x; return this; }
            Builder setContentTitle(String x) { n.title=x; return this; }
            Builder setContentText(String x) { n.text=x; return this; }
            Builder setOngoing(boolean x) { n.ongoing=x; return this; }
            Builder setVisibility(int x) { n.visibility=x; return this; }
            Builder setCategory(int x) { return this; }
            Builder setPriority(int x) { return this; }
            Notification build() { return n; }
        }
    }
    Notification.Builder mkbuilder(String channel) { return new Notification.Builder(); }
    String staleMessage(long t) { return "No current value; last reading " + t; }
    void applyReadingHeaderTimestamp(Notification.Builder b, long t) { b.n.time=t; }
    // METHOD
    public static void main(String[] args) {
        StaleNotificationHarness h = new StaleNotificationHarness();
        CurrentDisplaySource.Snapshot stale = new CurrentDisplaySource.Snapshot();
        Notification n = h.makeStaleReadingNotification(stale);
        if (!n.text.isEmpty()) throw new AssertionError("Stale number exposed on lock screen: " + n.text);
        if (!n.title.equals(n.wrappedStatus)) throw new AssertionError("Wrapping lost the complete status");
        if (!n.title.contains("No current value") || n.time != stale.time) throw new AssertionError("Missing status or original timestamp");
        if (!n.silent || !n.ongoing || n.icon != R.drawable.novalue || n.visibility != VISIBILITY_PUBLIC) throw new AssertionError("Status notification behavior changed");
        Notification empty = h.makeStaleReadingNotification(null);
        if (!empty.text.isEmpty()) throw new AssertionError("Null snapshot fabricated a value");
        System.out.println("PASS: production stale notification hides number, preserves status, timestamp, silent/public/ongoing behavior");
    }
}
"""
with tempfile.TemporaryDirectory() as tmp:
    p = Path(tmp) / "StaleNotificationHarness.java"
    p.write_text(java.replace("// METHOD", method))
    subprocess.run(["javac", str(p)], check=True)
    subprocess.run(["java", "-cp", tmp, "StaleNotificationHarness"], check=True)

