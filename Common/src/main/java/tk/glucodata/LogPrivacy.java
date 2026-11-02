package tk.glucodata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Process-local aliases; raw identities never enter the trace ring or log sinks. */
@androidx.annotation.Keep
public final class LogPrivacy {
    private static final Map<String, String> identities = new LinkedHashMap<>();
    private static int nextSensor;
    private static final java.util.Set<String> nativeRegistered = new java.util.HashSet<>();
    private static final Pattern SECRET = Pattern.compile(
            "(?i)passcode|password|authenticationdata|sensdorident|sensorident|unlockpayload|unlockkey|"
            + "(?:access|refresh|auth)[_ -]?token|\\btoken\\b|\\buid\\b|\\bdataptr\\b|"
            + "\\b(?:key|auth|credentials?|authorization)\\b|(?:session|shared)[_ -]?key|(?:challenge|response|payload|secret)\\s*[:=]\\s*(?!omitted)");
    private static final Pattern MAC = Pattern.compile("(?i)(?<![0-9a-f])(?:[0-9a-f]{2}:){5}[0-9a-f]{2}(?![0-9a-f])");
    private static final Pattern SERIAL = Pattern.compile("(?<![A-Za-z0-9])[0-9][A-Z0-9]{10,12}(?![A-Za-z0-9])");
    private static final Pattern POINTER = Pattern.compile("(?i)0x[0-9a-f]{6,}");
    private static final Pattern HEX = Pattern.compile("(?i)(?<![a-z0-9])[0-9a-f]{12,}(?![a-z0-9])|(?:[0-9a-f]{2}[ ,]){7,}[0-9a-f]{2}");
    private LogPrivacy() { }

    public static String registerSensor(String serial) {
        sensorAlias(serial);
        return serial;
    }

    public static synchronized String sensorAlias(String serial) {
        if (serial == null || serial.isEmpty()) return "sensor-unknown";
        String alias = identities.get(serial);
        if (alias == null) {
            alias = "sensor-" + (++nextSensor);
            identities.put(serial, alias);
        }
        register(serial, alias);
        return alias;
    }

    public static synchronized void registerAddress(String serial, String address) {
        if (address == null || address.isEmpty()) return;
        String alias = sensorAlias(serial);
        identities.put(address, alias);
        register(address, alias);
    }

    private static void register(String identity, String alias) {
        if (Applic.Nativesloaded && !nativeRegistered.contains(identity)) {
            try { registerNative(identity, alias); nativeRegistered.add(identity); }
            catch (UnsatisfiedLinkError ignored) { /* startup / old native library: Java stays protected */ }
        }
    }
    private static native void registerNative(String identity, String alias);

    public static synchronized String sanitize(String message) {
        if (message == null) return null;
        if (SECRET.matcher(message).find()) return "[sensitive log omitted]";
        for (Map.Entry<String, String> entry : identities.entrySet())
            message = message.replace(entry.getKey(), entry.getValue());
        message = MAC.matcher(message).replaceAll("[device]");
        message = SERIAL.matcher(message).replaceAll("[sensor]");
        message = POINTER.matcher(message).replaceAll("[address omitted]");
        return HEX.matcher(message).replaceAll("[bytes omitted]");
    }
}
