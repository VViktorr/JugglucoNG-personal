package tk.glucodata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.glucodata.FontCatalog
import tk.glucodata.R
import tk.glucodata.composeFamily

@Composable
internal fun FontPickerDialog(current: FontCatalog.Family, onSelect: (FontCatalog.Family) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_font_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FontCatalog.Family.values().forEach { family ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(family) }.padding(vertical = 8.dp)) {
                        RadioButton(selected = current == family, onClick = { onSelect(family) })
                        Text(family.displayName(context), fontFamily = family.composeFamily(context), modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
