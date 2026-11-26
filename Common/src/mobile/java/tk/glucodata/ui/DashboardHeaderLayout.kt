package tk.glucodata.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Space between the reading card and the sensor panel. */
internal val HeroSensorGap = 4.dp

private const val HERO_SHARE = 0.7f
private const val SENSOR_SHARE = 0.3f
/** However little the reading needs, the sensor panel never becomes the larger card. */
private const val SENSOR_MAX_SHARE = 0.5f
/** The panel grows in whole steps, so a counter gaining a digit rarely moves the split. */
private val SensorGrowthStep = 8.dp

/**
 * The two cards' default widths in [availablePx] (the row less its gap): 70 / 30, rounded
 * the way a weighted `Row` rounds them. `first` is the reading card, `second` the panel.
 */
internal fun heroSensorBaseSplit(availablePx: Int): Pair<Int, Int> {
    val available = availablePx.coerceAtLeast(0)
    val unit = available / (HERO_SHARE + SENSOR_SHARE)
    var hero = (unit * HERO_SHARE).roundToInt()
    val sensor = (unit * SENSOR_SHARE).roundToInt()
    hero += (available - hero - sensor).sign
    return hero to available - hero
}

/**
 * How wide the sensor panel is. It keeps its 30% while its lines fit there; when they do
 * not, it takes width the reading card is not using, and nothing more.
 *
 * @param preferredPx the width at which none of the panel's own lines is cut.
 * @param heroNeedPx the width the reading card's content takes at its full size, or null
 *   when the card has nothing to give (a wrapped status line, paired sensors, a value that
 *   is already shrunk to fit).
 */
internal fun sensorPanelWidth(availablePx: Int, preferredPx: Int, heroNeedPx: Int?, stepPx: Int): Int {
    val base = heroSensorBaseSplit(availablePx).second
    if (heroNeedPx == null || preferredPx <= base) return base
    val limit = minOf(availablePx - heroNeedPx, (availablePx * SENSOR_MAX_SHARE).roundToInt())
    if (limit <= base) return base
    val step = stepPx.coerceAtLeast(1)
    val steps = (preferredPx - base + step - 1) / step
    return minOf(base + steps * step, limit)
}

/** Whether the panel's lines are cut even with all the width the reading card can spare. */
internal fun isSensorPanelCrowded(availablePx: Int, preferredPx: Int, heroRoomyNeedPx: Int?): Boolean =
    heroRoomyNeedPx != null &&
        preferredPx > heroSensorBaseSplit(availablePx).second &&
        preferredPx > availablePx - heroRoomyNeedPx

/**
 * The portrait header row: the reading card and, when there is a sensor, its panel, both
 * as tall as the taller of the two.
 *
 * A weighted `Row` gave the panel 30% whatever was in it, so on a narrow screen with a
 * large font its top line ran out of room while the reading card beside it stood half
 * empty. Here the panel asks for the width its lines need and the split moves into the
 * reading card's spare width — see [sensorPanelWidth]. With enough room for both, the
 * split is the same 70 / 30 as before.
 *
 * When the spare width is not enough and the reading card has a tighter arrangement to
 * fall back on, the row says so through [onPanelCrowded] and the card takes it.
 *
 * @param heroNeedPx see [sensorPanelWidth].
 * @param heroRoomyNeedPx what the reading card needs in the arrangement it would choose
 *   with the row to itself, or null when it has no tighter one. The answer to "is the
 *   panel crowded" is taken against this, never against the arrangement on screen, so
 *   it cannot flip back and forth.
 * @param content the reading card, then optionally the sensor panel.
 */
@Composable
internal fun HeroSensorSplit(
    heroNeedPx: Int?,
    heroRoomyNeedPx: Int?,
    onPanelCrowded: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val need by rememberUpdatedState(heroNeedPx)
    val roomyNeed by rememberUpdatedState(heroRoomyNeedPx)
    val reportCrowded by rememberUpdatedState(onPanelCrowded)
    // One policy for the life of the row: the need is read while measuring, so a new
    // reading costs a layout pass and the header's other animations cost none.
    val policy = remember(scope) {
        val panelWidth = AnimatedPanelWidth(scope)
        MeasurePolicy { measurables, constraints ->
            val hero = measurables[0]
            val sensor = measurables.getOrNull(1)
            val totalWidth = constraints.maxWidth
            if (sensor == null) {
                reportCrowded(false)
                val height = constraints.constrainHeight(hero.minIntrinsicHeight(totalWidth))
                val placeable = hero.measure(Constraints.fixed(totalWidth, height))
                return@MeasurePolicy layout(totalWidth, height) { placeable.placeRelative(0, 0) }
            }
            val gap = HeroSensorGap.roundToPx()
            val available = (totalWidth - gap).coerceAtLeast(0)
            val heroNeed = need
            val base = heroSensorBaseSplit(available).second
            val preferred = sensor.maxIntrinsicWidth(Constraints.Infinity)
            reportCrowded(isSensorPanelCrowded(available, preferred, roomyNeed))
            val target = sensorPanelWidth(
                availablePx = available,
                preferredPx = preferred,
                heroNeedPx = heroNeed,
                stepPx = SensorGrowthStep.roundToPx()
            )
            // The move is animated, but the reading is never squeezed while it runs: when
            // the value grows a digit the panel gives the width back at once.
            val sensorWidth = panelWidth.widthFor(target, totalWidth)
                .coerceIn(base, maxOf(base, available - (heroNeed ?: available)))
            val heroWidth = available - sensorWidth
            val height = constraints.constrainHeight(
                maxOf(hero.minIntrinsicHeight(heroWidth), sensor.minIntrinsicHeight(sensorWidth))
            )
            val heroPlaceable = hero.measure(Constraints.fixed(heroWidth, height))
            val sensorPlaceable = sensor.measure(Constraints.fixed(sensorWidth, height))
            layout(totalWidth, height) {
                heroPlaceable.placeRelative(0, 0)
                sensorPlaceable.placeRelative(heroWidth + gap, 0)
            }
        }
    }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

/** The panel's width on its way to a new target. The first width, and a new row width, snap. */
private class AnimatedPanelWidth(private val scope: CoroutineScope) {
    private var width: Animatable<Int, AnimationVector1D>? = null
    private var rowWidth = -1

    fun widthFor(target: Int, rowWidth: Int): Int {
        val current = width
        if (current == null || rowWidth != this.rowWidth) {
            this.rowWidth = rowWidth
            width = Animatable(target, Int.VectorConverter)
            return target
        }
        if (current.targetValue != target) {
            scope.launch {
                current.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1))
            }
        }
        return current.value
    }
}
