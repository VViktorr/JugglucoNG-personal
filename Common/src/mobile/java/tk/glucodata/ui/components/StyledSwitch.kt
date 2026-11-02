package tk.glucodata.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * M3 Expressive styled Switch with checkmark icon when checked.
 * Use this everywhere instead of the regular Switch() for consistent styling.
 *
 * @param interactionSource Pass the one of a row that toggles this switch, so pressing the row
 * shows on the switch.
 */
@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null
) {
    // The thumb uses onPrimary, so pair its checkmark with primary. A container
    // foreground can have the same tone as the thumb in dynamic monochrome themes.
    val colors = SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        checkedIconColor = MaterialTheme.colorScheme.primary
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = if (enabled) colors.checkedIconColor else colors.disabledCheckedIconColor
                )
            }
        } else null
    )
}
