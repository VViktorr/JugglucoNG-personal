package tk.glucodata

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import tk.glucodata.settings.observeChanges

/** Observe local changes and incoming WearPrefsSync updates without restarting a screen. */
@Composable
fun rememberAppFont(): FontCatalog.Family {
    val context = LocalContext.current
    val prefs = remember(context) { FontCatalog.prefs(context) }
    var family by remember(prefs) { mutableStateOf(FontCatalog.appFamily(context)) }
    DisposableEffect(prefs) {
        val stop = prefs.observeChanges(FontCatalog.KEY, "app_use_system_font") {
            family = FontCatalog.appFamily(context)
        }
        onDispose(stop)
    }
    return family
}

fun FontCatalog.Family.composeFamily(context: android.content.Context): FontFamily =
    if (this == FontCatalog.Family.SYSTEM) FontFamily.Default else
        FontFamily(FontCatalog.typeface(context, this, 400, false))
