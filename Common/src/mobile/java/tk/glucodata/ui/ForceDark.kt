package tk.glucodata.ui

import android.os.Build
import android.view.View

/** Compose uses its own light/dark colour schemes; prevent system force-dark inversion. */
internal fun View.optOutOfForceDark() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isForceDarkAllowed = false
    }
}
