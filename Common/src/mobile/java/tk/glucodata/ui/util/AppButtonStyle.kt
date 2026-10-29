package tk.glucodata.ui.util

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared pill silhouette for actions and choices, including their pressed state. */
internal val AppButtonShape = RoundedCornerShape(50)

@Composable
internal fun appButtonShapes() = ButtonDefaults.shapes(
    shape = AppButtonShape,
    pressedShape = AppButtonShape
)

@Composable
internal fun appIconButtonShapes() = IconButtonDefaults.shapes(
    shape = AppButtonShape,
    pressedShape = AppButtonShape
)

@Composable
internal fun appFilterChipShapes() = FilterChipDefaults.shapes(
    shape = AppButtonShape,
    selectedShape = AppButtonShape,
    pressedShape = AppButtonShape
)

/** Pair each selection background with its matching text and icon colors. */
@Composable
internal fun appFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary
)
