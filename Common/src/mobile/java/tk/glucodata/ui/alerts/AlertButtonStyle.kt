package tk.glucodata.ui.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tk.glucodata.ui.util.ConnectedButtonGroup

private val alertPillShape = RoundedCornerShape(50)

@Composable
internal fun alertButtonShapes() = ButtonDefaults.shapes(
    shape = alertPillShape,
    pressedShape = alertPillShape
)

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
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
        unselectedContainerColor = Color.Transparent,
        unselectedContentColor = MaterialTheme.colorScheme.onSurface,
        unselectedBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        pillShapeForAll = true
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        shapes = FilterChipDefaults.shapes(
            shape = alertPillShape,
            selectedShape = alertPillShape,
            pressedShape = alertPillShape
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline
        )
    )
}
