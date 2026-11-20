package com.riffle.app.launcher

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

internal const val DOCK_HINT_TEST_TAG = "dock-hint"

/** How long a hint stands before it leaves by itself (never with a screen reader on). */
internal const val HINT_ON_SCREEN_MILLIS = 6_000L

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
 * A hint (ADR 0003, Amendments 12 and 21): one small Thick glass pill beside the Dock that says in a line what one Dock
 * gesture does. A tap on it dismisses it; it is drawn over the content but takes no touch outside itself, and it is
 * announced politely when it appears. It does not move, so reduced motion changes nothing about it. Which hint is due,
 * and when it leaves, is `Hints`' rule and [DockHintLayer]'s.
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
        modifier =
            Modifier
                .testTag(DOCK_HINT_TEST_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clickable(onClickLabel = stringResource(R.string.dock_hint_dismiss_action), onClick = onDismiss),
        weight = GlassWeight.Thick,
        shape = RiffleShapes.large,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = TEXT_MAX_WIDTH_DP.dp),
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val MAX_HINT_WIDTH_DP = 380
private const val TEXT_MAX_WIDTH_DP = 320
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
 * The hint on screen now and the touches that put it away, shared by the hint layer (which sets and ends it) and Home
 * (which draws the Stream handle while the press hint stands, so the teaching aid leaves with the hint).
 */
@Stable
internal class HintStage {
    var hint: Hint? by mutableStateOf(null)

    /** How many touches have gone down anywhere under [observe] so far. */
    var downs: Int by mutableIntStateOf(0)
        private set

    /** Watches the touches of whatever this modifier is put on, ahead of its children, and consumes nothing. */
    fun observe(): Modifier =
        Modifier.pointerInput(this) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { change -> change.pressed && !change.previousPressed }) downs++
                }
            }
        }
}

/** Whether a screen reader (touch exploration) is on: no timer then, since it needs more time than sighted use. */
@Composable
private fun screenReaderOn(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.isTouchExplorationEnabled == true
    }
}

/** Where the Dock stands, which way it is pulled, and the room to leave above a bottom Dock for the Home handle. */
internal class HintPlacement(
    val dockBounds: Rect?,
    val pull: DockPullDirection,
    val clearance: Dp,
)

/**
 * The hint layer: the due hint ([Hints.due], from what the person has seen and done and what is on screen now) drawn
 * beside the Dock, or nothing. A hint is retired the moment it is on screen ([HintEvent.Shown]), so it is shown at
 * most once. It leaves after [HINT_ON_SCREEN_MILLIS] (a timer of this composition, cancelled when the hint leaves; none
 * with a screen reader on), on the next touch anywhere ([HintStage.downs]), on a tap on it, or when what it teaches is
 * done or no longer applies. [legacyPressSeen] is the setting an earlier build kept for the press hint; it retires
 * that hint (nothing writes it any more, "Show hints again" clears it).
 */
@Composable
internal fun DockHintLayer(
    hints: HintsHost?,
    legacyPressSeen: Boolean,
    facts: HintContext,
    live: HintLiveInputs,
    stage: HintStage,
    placement: HintPlacement,
) {
    if (hints == null) return
    val stored by hints.progress.collectAsState()
    val surface by hints.surface.collectAsState()
    val progress =
        stored?.let { value -> if (legacyPressSeen) value.copy(retired = value.retired + Hint.DOCK_PRESS) else value }

    fun contextNow() =
        facts.copy(
            setUpShowing = facts.setUpShowing || surface != OnboardingSurface.None,
            dragging = live.dragging(),
            dockBusy = live.dockBusy(),
            stackCount = live.stackCount(),
        )
    val due by
        remember(progress, facts, surface) {
            derivedStateOf { progress?.let { value -> Hints.due(progress = value, context = contextNow()) } }
        }
    val showing = stage.hint
    val applies by
        remember(showing, facts, surface) {
            derivedStateOf { showing == null || Hints.stillApplies(showing, contextNow()) }
        }
    val hasRoom = placement.dockBounds != null
    // Nothing stands now and one is due: it comes on screen, and is retired as of now.
    LaunchedEffect(due, hasRoom, showing) {
        val next = due
        if (showing == null && next != null && hasRoom) {
            stage.hint = next
            hints.send(HintEvent.Shown(next))
        }
    }
    if (showing != null) {
        HintLifetime(stage = stage, showing = showing, applies = applies)
        DockHint(
            hint = showing,
            dockBounds = placement.dockBounds,
            pull = placement.pull,
            onDismiss = { stage.hint = null },
            // The Home handle stands above a bottom Dock on Home; over the Stream there is nothing to clear.
            clearance = if (showing.onHome) placement.clearance else 0.dp,
        )
    }
}

/**
 * How long [showing] stands: [HINT_ON_SCREEN_MILLIS] (none with a screen reader on), until the next touch anywhere, or
 * until it no longer [applies]. These are effects of this composition, so they end when the hint does.
 */
@Composable
private fun HintLifetime(
    stage: HintStage,
    showing: Hint,
    applies: Boolean,
) {
    val screenReader = screenReaderOn()
    LaunchedEffect(showing, screenReader) {
        if (!screenReader) {
            delay(HINT_ON_SCREEN_MILLIS)
            stage.hint = null
        }
    }
    LaunchedEffect(showing) {
        val before = stage.downs
        snapshotFlow { stage.downs }.first { count -> count != before }
        stage.hint = null
    }
    LaunchedEffect(showing, applies) { if (!applies) stage.hint = null }
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
