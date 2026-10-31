@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tk.glucodata.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tk.glucodata.R
import tk.glucodata.ui.components.CardPosition
import tk.glucodata.ui.components.SettingsSwitchItem
import tk.glucodata.ui.util.appButtonShapes

@Composable
internal fun ReportGroupPicker(
    selected: Set<LibreViewReportGroup>,
    onChange: (Set<LibreViewReportGroup>) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.lv_groups_title), style = MaterialTheme.typography.labelLarge)
            Text("${selected.size}/${LibreViewReportGroup.entries.size}", style = MaterialTheme.typography.labelMedium)
        }
        Row {
            TextButton(onClick = { onChange(LibreViewReportGroup.entries.toSet()) }, shapes = appButtonShapes()) {
                Text(stringResource(R.string.lv_groups_all))
            }
            TextButton(onClick = { onChange(emptySet()) }, shapes = appButtonShapes()) {
                Text(stringResource(R.string.lv_groups_clear))
            }
        }
        LibreViewReportGroup.entries.forEachIndexed { index, group ->
            SettingsSwitchItem(
                title = stringResource(group.labelResId),
                checked = group in selected,
                onCheckedChange = { checked -> onChange(if (checked) selected + group else selected - group) },
                position = when (index) {
                    0 -> CardPosition.TOP
                    LibreViewReportGroup.entries.lastIndex -> CardPosition.BOTTOM
                    else -> CardPosition.MIDDLE
                }
            )
        }
        Text(
            text = stringResource(if (selected.isEmpty()) R.string.lv_groups_choose else R.string.lv_groups_pdf_only),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
