package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import kotlin.math.roundToInt

internal const val DOCK_PRESS_HINT_TEST_TAG = "dock-press-hint"
internal const val DOCK_PRESS_HINT_DISMISS_TEST_TAG = "dock-press-hint-dismiss"

/**
 * Whether the first-run hint for pressing the Dock down is shown (ADR 0003, Amendment 3: the press "has no visible
 * affordance, so it needs a first-run hint and an accessibility action"). Once: until it is dismissed, or the Stream
 * has been opened by any route (the person has found it), it shows on Home when the Stream exists, and it is out of
 * the way of everything else: a running motion, a Library sheet, the Stream itself. (There is no reduced-interruption
 * mode yet to leave it out of; when Simple mode lands it joins this list.)
 */
internal fun shouldShowDockPressHint(
    seen: Boolean,
    streamAvailable: Boolean,
    streamShown: Boolean,
    libraryShown: Boolean,
    motionRunning: Boolean,
    choosing: Boolean,
): Boolean = !seen && streamAvailable && !streamShown && !libraryShown && !motionRunning && !choosing

/**
 * Where the hint's top-left lies: beyond the Dock ([dock], in root coordinates) toward the screen's interior ([pull] is
 * the way out of the Dock), centred along it and held inside a root of [root] with [margin] px to spare. [size] is the
 * hint's own.
 */
internal fun dockPressHintOffset(
    dock: Rect,
    size: IntSize,
    pull: DockPullDirection,
    root: IntSize,
    gap: Int,
    margin: Int,
): IntOffset {
    val centreX = dock.center.x + pull.unitX * (dock.width / 2f + gap + size.width / 2f)
    val centreY = dock.center.y + pull.unitY * (dock.height / 2f + gap + size.height / 2f)
    val x = (centreX - size.width / 2f).roundToInt()
    val y = (centreY - size.height / 2f).roundToInt()
    return IntOffset(
        x = x.coerceIn(margin, maxOf(margin, root.width - size.width - margin)),
        y = y.coerceIn(margin, maxOf(margin, root.height - size.height - margin)),
    )
}

/**
 * The hint: one Thick glass pill beside the Dock that says what the Dock does when pressed, with a button that
 * dismisses it for good. It is drawn over the content but takes no touch outside itself, and it is announced when it
 * appears.
 */
@Composable
internal fun DockPressHint(
    dockBounds: Rect?,
    pull: DockPullDirection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (dockBounds == null) return
    val gap = HINT_GAP_DP.dp
    val margin = HINT_MARGIN_DP.dp
    val maxWidth = MAX_HINT_WIDTH_DP.dp
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            DockPressHintPill(onDismiss = onDismiss)
        },
    ) { measurables, constraints ->
        val placeable =
            measurables.single().measure(
                Constraints(maxWidth = minOf(constraints.maxWidth, maxWidth.roundToPx())),
            )
        val offset =
            dockPressHintOffset(
                dock = dockBounds,
                size = IntSize(placeable.width, placeable.height),
                pull = pull,
                root = IntSize(constraints.maxWidth, constraints.maxHeight),
                gap = gap.roundToPx(),
                margin = margin.roundToPx(),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offset) }
    }
}

@Composable
private fun DockPressHintPill(onDismiss: () -> Unit) {
    GlassSurface(
        modifier = Modifier.testTag(DOCK_PRESS_HINT_TEST_TAG).semantics { liveRegion = LiveRegionMode.Polite },
        weight = GlassWeight.Thick,
        shape = RiffleShapes.large,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH_DP.dp),
                text = stringResource(R.string.dock_press_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                modifier = Modifier.testTag(DOCK_PRESS_HINT_DISMISS_TEST_TAG),
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.dock_press_hint_dismiss))
            }
        }
    }
}

private const val MAX_HINT_WIDTH_DP = 380
private const val TEXT_MAX_WIDTH_DP = 260
private const val HINT_GAP_DP = 12
private const val HINT_MARGIN_DP = 16
