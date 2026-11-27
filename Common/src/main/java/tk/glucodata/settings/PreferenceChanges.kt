package tk.glucodata.settings

import android.content.SharedPreferences

internal fun SharedPreferences.observeChanges(
    vararg keys: String,
    onChange: () -> Unit,
): () -> Unit {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in keys) onChange()
    }
    registerOnSharedPreferenceChangeListener(listener)
    onChange()
    return { unregisterOnSharedPreferenceChangeListener(listener) }
}
