package com.riffle.app.launcher

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.onboarding.OnboardingSurface
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.hints.Hint
import com.riffle.core.domain.launcher.hints.HintContext
import com.riffle.core.domain.launcher.hints.HintEvent
import com.riffle.core.domain.launcher.hints.Hints
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

internal const val DOCK_HINT_TEST_TAG = "dock-hint"
internal const val DOCK_HINT_DISMISS_TEST_TAG = "dock-hint-dismiss"

/** The words of a hint: one short sentence that says the gesture. */
@StringRes
internal fun Hint.textRes(): Int =
    when (this) {
        Hint.DOCK_PRESS -> R.string.dock_press_hint
        Hint.DOCK_MENU -> R.string.dock_menu_hint
        Hint.DOCK_PULL -> R.string.dock_pull_hint
        Hint.DOCK_SCRUB -> R.string.dock_scrub_hint
    }

/**
 * Where the hint's top-left lies: beyond the Dock ([dock], in root coordinates) toward the screen's interior ([pull] is
 * the way out of the Dock), centred along it and held inside a root of [root] with [margin] px to spare. [size] is the
 * hint's own. [clearance] is further room to leave beside the Dock, along [pull], for something that stands there (the
 * Home handle above a bottom Dock), so the hint never covers it.
 */
internal fun dockPressHintOffset(
    dock: Rect,
    size: IntSize,
    pull: DockPullDirection,
    root: IntSize,
    gap: Int,
    margin: Int,
    clearance: Int = 0,
): IntOffset {
    val gap = gap + clearance
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
 * A hint (ADR 0003, Amendment 12): one Thick glass pill beside the Dock that says what one Dock gesture does, with a
 * button that dismisses it for good. It is drawn over the content but takes no touch outside itself, and it is
 * announced when it appears. It does not move, so reduced motion changes nothing about it. Which hint is due, and
 * when, is `Hints`' rule.
 */
@Composable
internal fun DockHint(
    hint: Hint,
    dockBounds: Rect?,
    pull: DockPullDirection,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Room to leave between the Dock and the hint for what stands there: the Home handle above a bottom Dock. */
    clearance: Dp = 0.dp,
) {
    if (dockBounds == null) return
    val gap = HINT_GAP_DP.dp
    val margin = HINT_MARGIN_DP.dp
    val maxWidth = MAX_HINT_WIDTH_DP.dp
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            DockHintPill(text = stringResource(hint.textRes()), onDismiss = onDismiss)
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
                clearance = clearance.roundToPx(),
            )
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(offset) }
    }
}

@Composable
private fun DockHintPill(
    text: String,
    onDismiss: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.testTag(DOCK_HINT_TEST_TAG).semantics { liveRegion = LiveRegionMode.Polite },
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
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                modifier = Modifier.testTag(DOCK_HINT_DISMISS_TEST_TAG),
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

/**
 * What changes while a hint stands or waits, read where the hint is drawn (so only the hint layer recomposes when one
 * flips, not Home): whether something is being dragged, whether the Dock is moving or choosing, and how many stacks
 * the Stream has.
 */
internal class HintLiveInputs(
    val dragging: () -> Boolean,
    val dockBusy: () -> Boolean,
    val stackCount: () -> Int,
)

/**
 * The hint layer: the due hint ([Hints.due], from what the person has seen and done and what is on screen now) drawn
 * beside the Dock, or nothing. [legacyPressSeen] is the setting an earlier build kept for the press hint; it retires
 * that hint (nothing writes it any more, "Show hints again" clears it).
 */
@Composable
internal fun DockHintLayer(
    hints: HintsHost?,
    legacyPressSeen: Boolean,
    facts: HintContext,
    live: HintLiveInputs,
    dockBounds: Rect?,
    pull: DockPullDirection,
    clearance: Dp,
) {
    if (hints == null) return
    val stored by hints.progress.collectAsState()
    val surface by hints.surface.collectAsState()
    val progress =
        stored?.let { value -> if (legacyPressSeen) value.copy(retired = value.retired + Hint.DOCK_PRESS) else value }
    val due by
        remember(progress, facts, surface) {
            derivedStateOf {
                progress?.let { value ->
                    Hints.due(
                        progress = value,
                        context =
                            facts.copy(
                                setUpShowing = facts.setUpShowing || surface != OnboardingSurface.None,
                                dragging = live.dragging(),
                                dockBusy = live.dockBusy(),
                                stackCount = live.stackCount(),
                            ),
                    )
                }
            }
        }
    due?.let { hint ->
        DockHint(
            hint = hint,
            dockBounds = dockBounds,
            pull = pull,
            onDismiss = { hints.send(HintEvent.Dismissed(hint)) },
            // The Home handle stands above a bottom Dock on Home; over the Stream there is nothing to clear.
            clearance = if (hint.onHome) clearance else 0.dp,
        )
    }
}

/**
 * Reports what the person does to the hints, with no drawing: Home coming to the front, the Stream opening by any
 * route, the Library opening (by the Dock's pull, or another way), a hold and slide changing the stack, and a Dock
 * menu opening.
 */
@Composable
internal fun DockHintEvents(
    hints: HintsHost?,
    atHome: Boolean,
    streamShown: Boolean,
    libraryShown: Boolean,
    dockPull: DockPullState,
    choice: DockChoiceState,
) {
    if (hints == null) return
    val send by rememberUpdatedState(hints.send)
    LaunchedEffect(atHome) { if (atHome) send(HintEvent.HomeVisited) }
    LaunchedEffect(streamShown) { if (streamShown) send(HintEvent.StreamOpened) }
    LaunchedEffect(libraryShown) { if (libraryShown) send(HintEvent.LibraryOpened(byDockPull = false)) }
    LaunchedEffect(dockPull) {
        snapshotFlow { dockPull.pendingLibraryShown }.filter { it }.collect { send(HintEvent.LibraryOpened(true)) }
    }
    LaunchedEffect(choice) {
        snapshotFlow { choice.sliding }.filter { it }.collect { send(HintEvent.StackScrubbed) }
    }
    LaunchedEffect(choice) {
        snapshotFlow { choice.menuOpenings }.filter { it > 0 }.collect { send(HintEvent.DockMenuOpened) }
    }
}
