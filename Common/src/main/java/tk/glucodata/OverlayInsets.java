package tk.glucodata;

import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

final class OverlayInsets {
    private OverlayInsets() {}

    static void apply(View view, int padding) {
        view.setOnApplyWindowInsetsListener((panel, insets) -> {
            Insets edges = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            panel.setPadding(edges.left + padding, edges.top + padding,
                    edges.right + padding, edges.bottom + padding);
            return WindowInsets.CONSUMED;
        });
    }
}
