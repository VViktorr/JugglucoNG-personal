package tk.glucodata;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared, stable family IDs for phone, watch, Canvas and widgets. */
public final class FontCatalog {
    public static final String KEY = "app_font_family";
    public static final String FILE = "tk.glucodata_preferences";
    public enum Family {
        IBM_PLEX("IBM Plex Sans", R.font.ibm_plex_sans_var, "ibmplex.txt"),
        SYSTEM("System", 0, null),
        HARMONYOS("HarmonyOS Sans", R.font.harmonyos, "harmonyos.txt"),
        COMFORTAA("Comfortaa", R.font.comfortaa, "comfortaa.txt"),
        UBUNTU("Ubuntu", R.font.ubuntu, "ubuntu.txt"),
        ROBOTO_CONDENSED("Roboto Condensed", R.font.robotocondensed, "robotocondensed.txt"),
        OSWALD("Oswald", R.font.oswald, "oswald.txt"),
        RUBIK("Rubik", R.font.rubik, "rubik.txt"),
        POPPINS("Poppins", R.font.poppins, "poppins.txt"),
        PLUS_JAKARTA("Plus Jakarta Sans", R.font.plusjakartasans, "plusjakartasans.txt"),
        NOTO_SANS("Noto Sans", R.font.notosans, "notosans.txt"),
        URBANIST("Urbanist", R.font.urbanist, "urbanist.txt"),
        INTER("Inter", R.font.inter, "inter.txt"),
        DM_SANS("DM Sans", R.font.dmsans, "dmsans.txt"),
        ROBOTO_FLEX("Roboto Flex", R.font.robotoflex, "robotoflex.txt");
        public final String label;
        public final int resource;
        public final String licenseAsset;
        Family(String label, int resource, String licenseAsset) {
            this.label = label; this.resource = resource; this.licenseAsset = licenseAsset;
        }
        public String displayName(Context context) {
            return this == SYSTEM ? context.getString(R.string.app_font_system) : label;
        }
    }
    private FontCatalog() {}
    private static final Map<String, Typeface> CACHE = new HashMap<>();
    public static Family parse(String name, Family fallback) {
        try { return Family.valueOf(name); } catch (Exception ignored) { return fallback; }
    }
    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public static Family appFamily(Context context) {
        SharedPreferences p = prefs(context);
        Family legacy = p.getBoolean("app_use_system_font", false) ? Family.SYSTEM : Family.IBM_PLEX;
        return parse(p.getString(KEY, legacy.name()), legacy);
    }
    /** Until explicitly selected, retain per-surface legacy typography. */
    public static boolean hasSelection(Context context) { return !prefs(context).getString(KEY, "").isEmpty(); }
    public static void select(Context context, Family family) {
        prefs(context).edit().putString(KEY, family.name())
            .putBoolean("app_use_system_font", family == Family.SYSTEM).apply();
        WearPrefsSync.push();
    }
    public static synchronized Typeface typeface(Context context, Family family, int weight, boolean italic) {
        String key = family.name() + weight + italic;
        Typeface cached = CACHE.get(key);
        if (cached != null) return cached;
        Typeface base = Typeface.DEFAULT;
        try {
            if (family.resource != 0) base = ResourcesCompat.getFont(context, family.resource);
        } catch (RuntimeException ignored) { }
        if (base == null) base = Typeface.DEFAULT;
        Typeface result = Build.VERSION.SDK_INT >= 28 ? Typeface.create(base, weight, italic) :
            Typeface.create(base, (weight >= 600 ? Typeface.BOLD : Typeface.NORMAL) | (italic ? Typeface.ITALIC : 0));
        CACHE.put(key, result);
        return result;
    }
    public static Family widgetFamily(Context context, Family fallback) {
        return parse(context.getApplicationContext().getSharedPreferences("home_widget_defaults", Context.MODE_PRIVATE)
            .getString("global_widget_font_family", fallback.name()), fallback);
    }
    public static Typeface appTypeface(Context context, int weight) {
        return typeface(context, appFamily(context), weight, false);
    }
    private static final Map<TextView, Family> APPLIED = new WeakHashMap<>();
    public static void applyTree(View view) {
        if (!hasSelection(view.getContext())) return;
        Family family = appFamily(view.getContext());
        applyTree(view, family);
    }
    private static void applyTree(View view, Family family) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (APPLIED.get(text) != family) {
                Typeface old = text.getTypeface();
                int weight = old == null ? 400 : Build.VERSION.SDK_INT >= 28 ? old.getWeight() : old.isBold() ? 700 : 400;
                text.setTypeface(typeface(view.getContext(), family, weight, old != null && old.isItalic()));
                APPLIED.put(text, family);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i), family);
        }
    }
    public static void install(Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a, Bundle b) {
                View root = a.getWindow().getDecorView();
                root.getViewTreeObserver().addOnGlobalLayoutListener(() -> applyTree(root));
            }
            public void onActivityResumed(Activity a) { applyTree(a.getWindow().getDecorView()); }
            public void onActivityStarted(Activity a) {}
            public void onActivityPaused(Activity a) {}
            public void onActivityStopped(Activity a) {}
            public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {}
        });
    }
}
