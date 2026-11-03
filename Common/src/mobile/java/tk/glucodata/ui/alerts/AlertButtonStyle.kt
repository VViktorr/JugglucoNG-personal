package tk.glucodata.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import tk.glucodata.ui.util.appFilterChipColors
import tk.glucodata.ui.util.appFilterChipShapes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.util.ConnectedButtonGroup
import tk.glucodata.ui.util.appButtonShapes

@Composable
internal fun alertButtonShapes() = appButtonShapes()

/** The libre2-hybrid appearance, retaining Material's checkable selection controls. */
@Composable
internal fun <T> AlertOptionButtonGroup(
    options: List<T>,
    selectedOption: T? = null,
    selectedOptions: List<T> = emptyList(),
    onOptionSelected: (T) -> Unit,
    label: @Composable (T) -> Unit,
    labelText: ((T) -> String)? = null,
    icon: (@Composable (T) -> ImageVector?)? = null,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = false,
    itemHeight: Dp = 40.dp
) {
    ConnectedButtonGroup(
        options = options,
        selectedOption = selectedOption,
        selectedOptions = selectedOptions,
        onOptionSelected = onOptionSelected,
        label = label,
        labelText = labelText,
        icon = icon,
        modifier = modifier,
        multiSelect = multiSelect,
        itemHeight = itemHeight,
    )
}

@Composable
internal fun AlertChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    tk.glucodata.ui.util.SettingsChoiceChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
    )
}
