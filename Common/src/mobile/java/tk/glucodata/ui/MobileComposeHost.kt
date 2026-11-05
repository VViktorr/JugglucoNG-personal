package tk.glucodata.ui

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal class AppFontPreference(private val prefs: SharedPreferences) {
    var family by mutableStateOf(tk.glucodata.FontCatalog.appFamily(tk.glucodata.Applic.app))
        private set

    fun select(selected: tk.glucodata.FontCatalog.Family) {
        family = selected
        tk.glucodata.FontCatalog.select(tk.glucodata.Applic.app, selected)
    }
}

internal val LocalAppFontPreference = staticCompositionLocalOf<AppFontPreference?> { null }

/**
 * Mobile Compose entry point (plan P1/Q1, category R). Registered from the
 * mobile `Specific.registerBridges`; MainActivity calls it through [ComposeHost]
 * instead of resolving `ComposeHostKt` by name.
 */
object MobileComposeHost : ComposeHost {
    override fun setComposeContent(activity: AppCompatActivity, legacyView: View?) {
        // Hide the native legacy view (histogram/nanovg) to prevent double-rendering,
        // GPU overdraw, and visual glitches (bleeding through navbar).
        legacyView?.visibility = View.GONE
        activity.window.decorView.optOutOfForceDark()

        activity.setContent {
            val prefs = activity.getSharedPreferences(activity.packageName + "_preferences", Context.MODE_PRIVATE)
            val savedTheme = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            var themeMode by remember { mutableStateOf(ThemeMode.valueOf(savedTheme)) }

            val appFont = remember(prefs) { AppFontPreference(prefs) }
            CompositionLocalProvider(LocalAppFontPreference provides appFont) {
                JugglucoTheme(themeMode = themeMode) {
                    MainApp(
                        themeMode = themeMode,
                        onThemeChanged = { newMode ->
                            themeMode = newMode
                            prefs.edit().putString("theme_mode", newMode.name).apply()
                        }
                    )
                }
            }
        }
    }
}
