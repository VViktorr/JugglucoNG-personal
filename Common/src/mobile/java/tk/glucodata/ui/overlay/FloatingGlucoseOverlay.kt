package tk.glucodata.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import android.view.MotionEvent
import android.content.Intent
import tk.glucodata.composeFamily
import android.graphics.Typeface // Added for Google Sans
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import tk.glucodata.service.FloatingGlucoseService.CutoutData
import tk.glucodata.service.FloatingGlucoseService.CutoutEdge
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import tk.glucodata.data.settings.FloatingSettingsRepository
import tk.glucodata.ui.theme.MainFontFile
import tk.glucodata.ui.GlucosePoint
import tk.glucodata.ui.components.TrendIndicator
import tk.glucodata.logic.TrendEngine
import tk.glucodata.data.calibration.CalibrationManager
import tk.glucodata.ui.getDisplayValues
import tk.glucodata.CurrentDisplaySource
import tk.glucodata.DisplayDataState
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.SensorIdentity
import tk.glucodata.UiRefreshBus
import tk.glucodata.MultiSensorSelection
import tk.glucodata.ui.PeerCurrentReading
import tk.glucodata.ui.SecondarySensorDisplay

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
internal fun FloatingGlucoseOverlay(
    repository: FloatingSettingsRepository,
    historyFlow: Flow<List<GlucosePoint>>,
    onUpdatePosition: (Int, Int) -> Unit,
    onDragFinished: () -> Unit,
    cutoutDataFlow: Flow<tk.glucodata.service.FloatingGlucoseService.CutoutData>
) {
    val context = LocalContext.current

    // Settings State
    val isTransparent by repository.isTransparent.collectAsState(initial = false)
    val showSecondary by repository.showSecondary.collectAsState(initial = false)
    val fontSource by repository.fontSource.collectAsState(initial = "APP")
    val fontSize by repository.fontSize.collectAsState(initial = FloatingSettingsRepository.DEFAULT_FONT_SIZE)
    val fontWeightSetting by repository.fontWeight.collectAsState(initial = "REGULAR")
    val showArrow by repository.showArrow.collectAsState(initial = true)
    val cornerRadius by repository.cornerRadius.collectAsState(initial = 28f)
    val opacity by repository.backgroundOpacity.collectAsState(initial = FloatingSettingsRepository.DEFAULT_BACKGROUND_OPACITY)
    val isDynamicIsland by repository.isDynamicIslandEnabled.collectAsState(initial = false)
    val islandNudge by repository.islandNudge.collectAsState(initial = FloatingSettingsRepository.DEFAULT_ISLAND_NUDGE)
    val islandManualOffset by repository.islandManualOffset.collectAsState(initial = FloatingSettingsRepository.DEFAULT_ISLAND_MANUAL_OFFSET)
    val manualGap by repository.islandGap.collectAsState(initial = 0f)
    val useSubtleOutline by repository.useSubtleOutline.collectAsState(initial = false)

    // Metrics State (from Service WindowInsets)
    val cutoutData by cutoutDataFlow.collectAsState(initial = CutoutData(0.dp, CutoutEdge.NONE))
    val cutoutEdge = cutoutData.edge
    val cutoutSize = cutoutData.size

    // Data State: History List
    val history by historyFlow.collectAsState(initial = emptyList())
    val refreshRevision by UiRefreshBus.revision.collectAsState(initial = 0L)
    
    // Derived Data
    val requestedSensorId = SensorIdentity.resolveMainSensor()
    val selectionRevision by MultiSensorSelection.revision.collectAsState()
    val readings by rememberOverlayReadings(
        requestedSensorId, showSecondary, Triple(refreshRevision, selectionRevision, history),
    ) { sensorId, includePeer ->
        withContext(Dispatchers.IO) {
            val snapshot = CurrentDisplaySource.resolveCurrent(Notify.glucosetimeout, sensorId)
            val resolvedSensorId = snapshot?.sensorId ?: sensorId
            val peers = if (includePeer && snapshot?.secondaryStr.isNullOrBlank()) {
                SecondarySensorDisplay.resolvePeers(SecondarySensorDisplay.selectedPeers(resolvedSensorId))
            } else emptyList()
            OverlayReadings(resolvedSensorId, snapshot, peers)
        }
    }
    // Retain the previous complete frame until the next main/peer pair is resolved.
    val currentSensorId = readings.sensorId
    val currentSnapshot = readings.snapshot
    val peerReadings = readings.peers
    val sensorHistory = remember(history, currentSensorId) {
        history.filter { currentSensorId != null && SensorIdentity.matches(it.sensorSerial, currentSensorId) }
    }
    val glucosePoint = sensorHistory.lastOrNull() ?: currentSnapshot?.let {
        GlucosePoint(it.autoValue, "", it.timeMillis, it.rawValue, it.rate, it.sensorId)
    }

    // The overlay only recomposes on new data, so once readings stop nothing would
    // ever notice the last one aging out. Re-read the clock until it crosses the
    // same timeout the widget and dashboard use, then show the no-data state.
    val latestReadingMillis = maxOf(currentSnapshot?.timeMillis ?: 0L, glucosePoint?.timestamp ?: 0L)
    val freshnessNow by produceState(System.currentTimeMillis(), latestReadingMillis, peerReadings) {
        while (true) {
            value = System.currentTimeMillis()
            // A peer can expire before the main reading, without another sensor update.
            val wait = (listOf(latestReadingMillis) + peerReadings.map { it.timeMillis })
                .mapNotNull { nextOverlayFreshnessCheckDelay(it, value) }.minOrNull() ?: break
            delay(wait)
        }
    }
    // Every layout (pill, side and top island) reads value and arrow from this.
    val displayPoint = overlayDisplayPoint(glucosePoint, currentSnapshot?.timeMillis ?: 0L, freshnessNow)
    
    // View Mode & Calibration
    val viewData = remember(currentSnapshot, glucosePoint, currentSensorId) {
        val resolvedViewMode = currentSnapshot?.viewMode
        val viewMode = if (resolvedViewMode != null) {
            resolvedViewMode
        } else if (!currentSensorId.isNullOrEmpty()) {
            runCatching {
                val snapshot = Natives.getSensorUiSnapshot(currentSensorId)
                if (snapshot != null && snapshot.size >= 2) snapshot[1].toInt() else 0
            }.getOrDefault(0)
        } else 0
        Pair(viewMode, Natives.getunit())
    }
    val viewMode = viewData.first
    val unitInt = viewData.second
    
    // Same estimator, window and raw/smoothed choice as every other arrow; the
    // overlay used to omit useRaw, so in raw view modes it regressed over the
    // smoothed series and tilted differently from the hero it floats next to.
    val trendResult = remember(sensorHistory, viewMode, unitInt, currentSnapshot) {
        if (sensorHistory.isNotEmpty()) {
            TrendEngine.calculateTrend(
                sensorHistory,
                useRaw = (viewMode == 1 || viewMode == 3),
                isMmol = (unitInt == 1)
            )
        } else {
            TrendEngine.TrendResult(TrendEngine.TrendState.Unknown, currentSnapshot?.rate ?: 0f, 0f, 0f, 0f)
        }
    }
    
    // Gap Calculation
    // Use manual gap if > 0, otherwise detected width. Default 70dp if all else fails.
    val isVerticalIsland = cutoutEdge == CutoutEdge.LEFT || cutoutEdge == CutoutEdge.RIGHT
    val finalGap = if (manualGap > 0f) {
        manualGap.dp
    } else if (cutoutSize > 0.dp) {
        cutoutSize
    } else {
        70.dp
    }
    // Styles
    val isDarkTheme = isSystemInDarkTheme()
    val finalBgColor = if (isTransparent) Color.Transparent else Color.Black.copy(alpha = opacity)
    val finalShape = RoundedCornerShape(cornerRadius.dp)
    val finalTextColor = if (isTransparent && !isDarkTheme) {
        // ui-guardrails: allow hex_color - drawn straight onto other apps, outside the app theme
        Color(0xFF27231F)
    } else {
        Color.White
    }
    val textOutlineColor = when {
        isTransparent && !isDarkTheme -> Color.White.copy(alpha = 0.88f)
        isTransparent -> Color.Black.copy(alpha = 0.58f)
        else -> Color.Black.copy(alpha = 0.28f)
    }
    val textShadow = Shadow(
        color = when {
            isTransparent && !isDarkTheme -> Color.White.copy(alpha = 1.0f)
            isTransparent -> Color.Black.copy(alpha = 0.62f)
            else -> Color.Black.copy(alpha = 0.42f)
        },
        offset = Offset(0f, if (isTransparent && !isDarkTheme) 0f else if (isTransparent) 1.2f else 1.5f),
        blurRadius = if (isTransparent && !isDarkTheme) 7f else if (isTransparent) 4.5f else 5f
    )
    val arrowOutlineColor = if (isTransparent && useSubtleOutline) textOutlineColor else null
    val arrowShadowColor = if (isTransparent && !useSubtleOutline) textShadow.color else null
    val overlayInteractionSource = remember { MutableInteractionSource() }
    val overlayIndication = ripple(
        bounded = true,
        color = if (isTransparent) {
            Color.White.copy(alpha = 0.24f)
        } else {
            Color.White.copy(alpha = 0.16f)
        }
    )
    var pendingDragX by remember { mutableFloatStateOf(0f) }
    var pendingDragY by remember { mutableFloatStateOf(0f) }
    
    // Drag Modifier
    val dragModifier = if (isDynamicIsland) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = {
                pendingDragX = 0f
                pendingDragY = 0f
                onDragFinished()
            },
            onDragCancel = {
                pendingDragX = 0f
                pendingDragY = 0f
                onDragFinished()
            }
        ) { change, dragAmount ->
            change.consume()
            pendingDragX += dragAmount.x
            pendingDragY += dragAmount.y
            val wholeX = pendingDragX.toInt()
            val wholeY = pendingDragY.toInt()
            if (wholeX != 0 || wholeY != 0) {
                pendingDragX -= wholeX
                pendingDragY -= wholeY
                onUpdatePosition(wholeX, wholeY)
            }
        }
    }

    // Font Logic
    val weightVal = when (fontWeightSetting) {
        "LIGHT" -> 300
        "MEDIUM" -> 500
        else -> 400
    }
    
    val selectedFont = tk.glucodata.rememberAppFont()
    val fontFamily = remember(fontSource, weightVal, selectedFont) {
        if (tk.glucodata.FontCatalog.hasSelection(context)) selectedFont.composeFamily(context)
        else
        if (fontSource == "APP") {
            try {
                FontFamily(Font(MainFontFile,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weightVal))))
            } catch (th: Throwable) {
                android.util.Log.w("FloatingGlucoseOverlay", "Variable font fallback activated", th)
                FontFamily(Font(MainFontFile))
            }
        } else {
            val familyName = if (weightVal >= 500) "google-sans-medium" else "google-sans"
            try { FontFamily(Typeface.create(familyName, Typeface.NORMAL)) }
            catch (e: Exception) { FontFamily.SansSerif }
        }
    }
    val fontWeight = FontWeight(weightVal)
    val inlineSpacing = (fontSize * 0.35f).coerceIn(4f, 10f).dp
    val pillHorizontalPadding = (fontSize * 0.85f).coerceIn(10f, 24f).dp
    val pillVerticalPadding = (fontSize * 0.45f).coerceIn(4f, 12f).dp
    val splitPadding = (fontSize * 0.28f).coerceIn(3f, 8f).dp
    val arrowSize = (fontSize * 0.9f).coerceIn(12f, 42f).dp
    val sideIslandHorizontalPadding = (fontSize * 0.55f).coerceIn(6f, 16f).dp
    val sideIslandVerticalPadding = (fontSize * 0.32f).coerceIn(3f, 8f).dp
    val sideIslandSplitPadding = (fontSize * 0.18f).coerceIn(2f, 6f).dp
    val sideIslandArrowSize = (fontSize * 0.78f).coerceIn(10f, 34f).dp
    val sideSecondarySpacing = (fontSize * 0.08f).coerceIn(1f, 4f).dp
    val sideSecondaryFontSize = fontSize * 0.58f

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val valueContent: @Composable () -> Unit = {
        if (displayPoint != null) {
            val point = displayPoint
            val unit = if (unitInt == 1) "mmol/L" else "mg/dL"
            val dvs = currentSnapshot?.displayValues ?: run {
                val isRawModeForCal = viewMode == 1 || viewMode == 3
                val hasCalibration = !CalibrationManager.shouldOverwriteSensorValues() &&
                    CalibrationManager.hasActiveCalibration(isRawModeForCal)
                val calibratedValue = if (hasCalibration) {
                    val baseValue = if (isRawModeForCal) point.rawValue else point.value
                    if (baseValue.isFinite() && baseValue > 0.1f) {
                        CalibrationManager.getCalibratedValue(baseValue, point.timestamp, isRawModeForCal)
                    } else {
                        null
                    }
                } else null
                getDisplayValues(point, viewMode, unit, calibratedValue)
            }
            val secondaryText = dvs.secondaryStr ?: SecondarySensorDisplay.fallback(
                dvs, currentSensorId, peerReadings, freshnessNow
            )?.primaryStr

            if (isDynamicIsland && isVerticalIsland) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingStyledText(
                        text = dvs.primaryStr,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        textColor = finalTextColor,
                        outlineColor = textOutlineColor,
                        shadow = textShadow,
                        useOutline = useSubtleOutline,
                        textAlign = TextAlign.Center
                    )
                    if (showSecondary && !secondaryText.isNullOrEmpty()) {
                        Spacer(Modifier.height(sideSecondarySpacing))
                        FloatingStyledText(
                            text = secondaryText,
                            fontSize = sideSecondaryFontSize,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            textColor = finalTextColor.copy(alpha = 0.7f),
                            outlineColor = textOutlineColor,
                            shadow = textShadow,
                            useOutline = useSubtleOutline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FloatingStyledText(
                        text = dvs.primaryStr,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight,
                        textColor = finalTextColor,
                        outlineColor = textOutlineColor,
                        shadow = textShadow,
                        useOutline = useSubtleOutline,
                        textAlign = TextAlign.End
                    )
                    if (showSecondary && !secondaryText.isNullOrEmpty()) {
                        Spacer(Modifier.width(inlineSpacing))
                        FloatingStyledText(
                            text = secondaryText,
                            fontSize = fontSize * 0.7f,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight,
                            textColor = finalTextColor.copy(alpha = 0.7f),
                            outlineColor = textOutlineColor,
                            shadow = textShadow,
                            useOutline = useSubtleOutline
                        )
                    }
                }
            }
        } else {
            FloatingStyledText(
                text = "---",
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                textColor = finalTextColor,
                outlineColor = textOutlineColor,
                shadow = textShadow,
                useOutline = useSubtleOutline
            )
        }
    }

    val arrowContent: @Composable () -> Unit = {
        if (showArrow && displayPoint != null) {
            TrendIndicator(
                trendResult = trendResult,
                modifier = Modifier.size(if (isDynamicIsland && isVerticalIsland) sideIslandArrowSize else arrowSize),
                color = finalTextColor,
                outlineColor = arrowOutlineColor,
                shadowColor = arrowShadowColor
            )
        } else {
            Spacer(Modifier.size(1.dp))
        }
    }
    
    // ROOT LAYOUT CHANGE: Use Column just for Vertical Offset Spacer if Island
    // We don't use 'Surface' as root for Island anymore, because we want split layout.
    
    // ROOT LAYOUT: Column for vertical offset spacer
    if (isDynamicIsland) {
        // Clip and click the pill itself, not the offset wrapper: the wrapper is
        // taller by the offset spacer, so its corner radius clamps differently
        // from the pill's own Surface and its arc cut into the bottom corners.
        val pillModifier = Modifier
            .clip(finalShape)
            .clickable(
                interactionSource = overlayInteractionSource,
                indication = overlayIndication
            ) { launchIntent?.let { context.startActivity(it) } }
        CutoutOffsetLayout(
            edge = cutoutEdge,
            centre = cutoutData.centre,
            nudge = islandNudge.dp,
            manualOffset = islandManualOffset.dp,
            modifier = Modifier
                .wrapContentSize()
                .then(dragModifier)
        ) {
            if (isVerticalIsland) {
                IslandLabels(
                    modifier = pillModifier,
                    gap = finalGap,
                    vertical = true,
                    anchor = cutoutData.anchor,
                    backgroundContent = {
                        Surface(
                            color = finalBgColor,
                            shape = finalShape,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(
                            start = sideIslandHorizontalPadding,
                            end = sideIslandHorizontalPadding,
                            top = sideIslandVerticalPadding + sideIslandSplitPadding,
                            bottom = sideIslandSplitPadding
                        )
                    ) {
                        valueContent()
                    }
                    Box(
                        modifier = Modifier.padding(
                            start = sideIslandHorizontalPadding,
                            end = sideIslandHorizontalPadding,
                            top = sideIslandSplitPadding,
                            bottom = sideIslandVerticalPadding + sideIslandSplitPadding
                        )
                    ) {
                        arrowContent()
                    }
                }
            } else {
                IslandLabels(
                    modifier = pillModifier,
                    gap = finalGap,
                    vertical = false,
                    anchor = cutoutData.anchor,
                    backgroundContent = {
                        Surface(
                            color = finalBgColor,
                            shape = finalShape,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(
                            start = pillHorizontalPadding,
                            top = pillVerticalPadding,
                            bottom = pillVerticalPadding
                        )
                    ) {
                        valueContent()
                    }
                    Box(
                        modifier = Modifier.padding(
                            end = pillHorizontalPadding,
                            top = pillVerticalPadding,
                            bottom = pillVerticalPadding
                        )
                    ) {
                        arrowContent()
                    }
                }
            }
        }
    } else {
        // ORIGINAL FLOATING LAYOUT (Unified Pill)
        Surface(
            color = finalBgColor,
            shape = finalShape,
            modifier = Modifier
                .wrapContentSize()
                .then(dragModifier)
                .clip(finalShape)
                .clickable(
                    interactionSource = overlayInteractionSource,
                    indication = overlayIndication
                ) { launchIntent?.let { context.startActivity(it) } }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = pillHorizontalPadding, vertical = pillVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(inlineSpacing)
            ) {
                valueContent()
                if (showArrow && displayPoint != null) {
                    TrendIndicator(
                        trendResult,
                        Modifier.size(arrowSize),
                        finalTextColor,
                        outlineColor = arrowOutlineColor,
                        shadowColor = arrowShadowColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingStyledText(
    text: String, 
    fontSize: Float, 
    fontFamily: FontFamily, 
    fontWeight: FontWeight, 
    textColor: Color, 
    outlineColor: Color,
    shadow: Shadow,
    useOutline: Boolean,
    textAlign: TextAlign = TextAlign.Start
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 3 })
                .togetherWith(fadeOut(tween(140)) + slideOutVertically(tween(220)) { it / 3 })
        },
        label = "FloatingReadingValue"
    ) { shownText ->
        if (useOutline) {
            Box {
                Text(
                    text = shownText,
                    color = outlineColor,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    style = TextStyle.Default.copy(
                        drawStyle = Stroke(
                            miter = 10f,
                            width = 2.6f,
                            join = StrokeJoin.Round
                        )
                    )
                )
                Text(
                    text = shownText,
                    color = textColor,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = textAlign
                )
            }
        } else {
            Text(
                text = shownText,
                color = textColor,
                fontSize = fontSize.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                textAlign = textAlign,
                style = TextStyle(shadow = shadow)
            )
        }
    }
}




@Composable
private fun IslandLabels(
    modifier: Modifier,
    gap: Dp,
    vertical: Boolean,
    anchor: FloatingIsland.CutoutAnchor?,
    backgroundContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(contents = listOf(backgroundContent, content), modifier = modifier) { (background, labels), constraints ->
        val gapPx = gap.roundToPx()
        val space = anchor?.labelSpace(gapPx)
        val maxAlong = space?.maxLabelExtent ?: Constraints.Infinity
        val labelConstraints = if (vertical) {
            constraints.copy(minHeight = 0, maxHeight = minOf(constraints.maxHeight, maxAlong))
        } else {
            constraints.copy(minWidth = 0, maxWidth = minOf(constraints.maxWidth, maxAlong))
        }
        val before = labels[0].measure(labelConstraints)
        val after = labels[1].measure(labelConstraints)
        val beforeAlong = if (vertical) before.height else before.width
        val afterAlong = if (vertical) after.height else after.width
        val beforeAcross = if (vertical) before.width else before.height
        val afterAcross = if (vertical) after.width else after.height
        val placement = space?.place(beforeAlong, afterAlong)
            ?: FloatingIsland.centreLabels(gapPx, beforeAlong, afterAlong)

        val acrossExtent = if (placement.stacked) beforeAcross + afterAcross else maxOf(beforeAcross, afterAcross)
        val backgroundStart = minOf(placement.before, placement.after)
        val backgroundExtent = maxOf(placement.before + beforeAlong, placement.after + afterAlong) - backgroundStart
        val backgroundConstraints = if (vertical) {
            Constraints.fixed(acrossExtent, backgroundExtent)
        } else {
            Constraints.fixed(backgroundExtent, acrossExtent)
        }
        val backdrop = background.single().measure(backgroundConstraints)
        val beforeCross = if (placement.stacked) 0 else (acrossExtent - beforeAcross) / 2
        val afterCross = if (placement.stacked) beforeAcross else (acrossExtent - afterAcross) / 2
        val width = if (vertical) acrossExtent else placement.extent
        val height = if (vertical) placement.extent else acrossExtent
        layout(width, height) {
            if (vertical) {
                backdrop.place(0, backgroundStart)
                before.place(beforeCross, placement.before)
                after.place(afterCross, placement.after)
            } else {
                backdrop.place(backgroundStart, 0)
                before.place(placement.before, beforeCross)
                after.place(placement.after, afterCross)
            }
        }
    }
}

/**
 * Puts the pill [content] a gap in from the screen edge it is docked to ([edge]), so that
 * it is centred on the cutout [centre] from that edge (0 when not known), then moved by
 * [nudge] away from the edge. The gap depends on the pill's own thickness across the edge,
 * so it is worked out from the measured pill; see [FloatingIsland].
 */
@Composable
private fun CutoutOffsetLayout(
    edge: CutoutEdge,
    centre: Dp,
    nudge: Dp,
    manualOffset: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val pill = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = FloatingIsland.edgeGap(
            centre = centre.toPx(),
            nudge = nudge.toPx(),
            thickness = FloatingIsland.thickness(edge, pill.width, pill.height).toFloat(),
            manualOffset = manualOffset.toPx()
        ).roundToInt()
        val placement = FloatingIsland.place(edge, pill.width, pill.height, gap)
        layout(placement.width, placement.height) {
            pill.place(placement.pillX, placement.pillY)
        }
    }
}

private const val OVERLAY_FRESHNESS_POLL_MS = 15_000L

/**
 * How long to wait before re-checking whether the reading at [latestReadingMillis]
 * has gone stale, or null once it has (or there is none) and nothing is left to
 * watch. Capped at [OVERLAY_FRESHNESS_POLL_MS] because coroutine delays run on
 * uptime, which stops during deep sleep: one long wait would wake far too late.
 */
internal fun nextOverlayFreshnessCheckDelay(
    latestReadingMillis: Long,
    nowMillis: Long,
    freshnessWindowMillis: Long = Notify.glucosetimeout
): Long? {
    if (latestReadingMillis <= 0L) return null
    val untilStale = latestReadingMillis + freshnessWindowMillis - nowMillis
    if (untilStale < 0L) return null
    return (untilStale + 1L).coerceAtMost(OVERLAY_FRESHNESS_POLL_MS)
}

/**
 * The point the overlay may show at [nowMillis]: the latest one while it is within
 * the widget/dashboard freshness window, null (the no-data state) once it is not.
 */
internal fun overlayDisplayPoint(
    latestPoint: GlucosePoint?,
    snapshotMillis: Long,
    nowMillis: Long,
    freshnessWindowMillis: Long = Notify.glucosetimeout
): GlucosePoint? {
    val isFresh = DisplayDataState.resolve(
        sensorPresent = true,
        currentTimestampMillis = snapshotMillis,
        latestHistoryTimestampMillis = latestPoint?.timestamp ?: 0L,
        freshnessWindowMillis = freshnessWindowMillis,
        nowMillis = nowMillis
    ).isFresh
    return latestPoint?.takeIf { isFresh }
}
