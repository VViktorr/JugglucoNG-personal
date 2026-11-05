package tk.glucodata;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;

/** The unabridged upstream notices are packaged in both APK variants. */
public final class FontLicenses {
    private FontLicenses() {}
    public static void show(Context context) {
        java.util.List<FontCatalog.Family> families = new java.util.ArrayList<>();
        for (FontCatalog.Family family : FontCatalog.Family.values()) {
            if (family.licenseAsset != null) families.add(family);
        }
        String[] labels = new String[families.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = families.get(i).displayName(context);
        new AlertDialog.Builder(context).setTitle(R.string.font_licenses_title)
            .setItems(labels, (dialog, index) -> showText(context, families.get(index).licenseAsset))
            .setNegativeButton(android.R.string.cancel, null).show();
    }
    private static void showText(Context context, String file) {
        String content;
        try (InputStream in = context.getAssets().open("font_licenses/" + file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            content = new String(out.toByteArray(), StandardCharsets.UTF_8)
                .replace("\\r\\n", "\n");
        } catch (java.io.IOException e) { return; }
        TextView text = new TextView(context);
        text.setText(content); text.setTextIsSelectable(true);
        int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        FontCatalog.applyTree(text);
        ScrollView scroll = new ScrollView(context); scroll.addView(text);
        new AlertDialog.Builder(context).setTitle(file).setView(scroll)
            .setPositiveButton(android.R.string.ok, null).show();
    }
}
