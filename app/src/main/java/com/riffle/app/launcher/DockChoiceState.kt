package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dock.DockChoice
import com.riffle.core.domain.launcher.dock.DockChoiceItem
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The selection while a Dock press is choosing (ADR 0003, Amendment 3, One gesture, third depth): which item the finger
 * has under it, whether it has gone far enough away to cancel, and the row it scrolls when held near an end.
 *
 * Nothing here decides anything: where choosing starts, which item is nearest, which end scrolls, when the finger
 * cancels and when a tick sounds are [DockChoice]'s. This holds the finger and the Dock's geometry, which the host
 * provides ([Environment]), and applies them each frame ([frame]) so the selection also follows a row that scrolls
 * under a still finger. [choosing], [selectedKey] and [cancelling] are snapshot state: the row, the tiles and the label
 * read them.
 */
@Stable
internal class DockChoiceState {
    /** Where the Dock is, read fresh at each use; set by whoever binds the Dock's gesture. */
    class Environment(
        /** A point in the Dock gesture's own coordinates, in the root's. */
        val toRoot: (Offset) -> Offset,
        /** Where each dynamic entry lies in the root, by key. */
        val entryBounds: () -> Map<String, Rect>,
        /** The Dock's area in the root, or null while it is not measured. */
        val dockBounds: () -> Rect?,
        /** The direction the Dock is pulled in, away from its edge. */
        val pull: DockPullDirection,
        val density: Float,
    )

    var choosing: Boolean by mutableStateOf(false)
        private set

    /** Whether the press started with the Stream already out (the contextual mode). */
    var fromContextual: Boolean = false
        private set

    /** The key of the item the finger has selected, or null when none can be (or the finger is cancelling). */
    var selectedKey: String? by mutableStateOf(null)
        private set

    /** Whether the finger is far enough from the Dock that lifting now cancels the choice. */
    var cancelling: Boolean by mutableStateOf(false)
        private set

    /** Whether holding near an end has scrolled the row since choosing began (it then stops revealing by itself). */
    var edgeScrolled: Boolean by mutableStateOf(false)
        private set

    var environment: Environment? = null

    /** Called for each light tick: choosing began, or the selection moved to another item. */
    var onTick: () -> Unit = {}

    /** The row's viewport in the root, and how it scrolls by pixels; set by the row while it is composed. */
    var rowViewport: Rect? by mutableStateOf(null)
    var scrollRowBy: ((Float) -> Float)? = null

    /** Where the finger is, in the root. Snapshot state, so holding it near an end of the row is seen as it happens. */
    private var finger: Offset by mutableStateOf(Offset.Zero)

    /**
     * Which way the finger's place scrolls the row now: -1 toward its start, +1 toward its end, 0 not at all (also
     * while cancelling, and without a row). The frames only run while this is not 0.
     */
    val edgeDirection: Int
        get() {
            val env = environment
            val viewport = rowViewport
            if (env == null || viewport == null || !isChoosingNotCancelling) return 0
            val alongRun = env.pull.unitY != 0f
            return DockChoice.edgeDirection(
                fingerPx = if (alongRun) finger.x else finger.y,
                viewportStartPx = if (alongRun) viewport.left else viewport.top,
                viewportEndPx = if (alongRun) viewport.right else viewport.bottom,
            )
        }

    private val isChoosingNotCancelling: Boolean get() = choosing && !cancelling

    /** What the entries' places are now, so the selection is looked at again whenever one moves (the row scrolled). */
    fun entryPlaces(): List<Pair<String, Rect>> =
        environment?.entryBounds()?.entries?.map { it.key to it.value }.orEmpty()

    /** A press is claimed: remember whether it started in the contextual mode. */
    fun prepare(fromContextual: Boolean) {
        this.fromContextual = fromContextual
    }

    /** Choosing starts with the finger at [position] (the gesture's own coordinates). */
    fun begin(position: Offset) {
        edgeScrolled = false
        selectedKey = null
        cancelling = false
        choosing = true
        moved(position, tickOnSelect = false)
        onTick()
    }

    /** The finger is at [position] (the gesture's own coordinates). */
    fun moved(
        position: Offset,
        tickOnSelect: Boolean = true,
    ) {
        if (!choosing) return
        val env = environment ?: return
        finger = env.toRoot(position)
        reselect(env, tickOnSelect)
    }

    /**
     * One frame of holding near an end: the row scrolls by what [seconds] come to, and the selection follows. Returns
     * whether the row moved; false once it has reached its end, so the frames can stop and the app can idle.
     */
    fun frame(seconds: Float): Boolean {
        val env = environment ?: return false
        val direction = edgeDirection
        var moved = false
        if (direction != 0) {
            edgeScrolled = true
            val consumed = scrollRowBy?.invoke(DockChoice.edgeScrollDp(direction, seconds) * env.density) ?: 0f
            moved = consumed != 0f
        }
        reselect(env, tickOnSelect = true)
        return moved
    }

    /** What is under the finger may have changed (an entry moved): look again. */
    fun refresh() {
        if (!choosing) return
        val env = environment ?: return
        reselect(env, tickOnSelect = true)
    }

    /** The press ended, committed or not. */
    fun end() {
        choosing = false
        selectedKey = null
        cancelling = false
    }

    private fun reselect(
        env: Environment,
        tickOnSelect: Boolean,
    ) {
        val alongRun = env.pull.unitY != 0f
        val dock = env.dockBounds()
        cancelling = dock != null && DockChoice.isCancelling(awayDp(env, dock), thicknessDp(env, dock, alongRun))
        val next =
            if (cancelling) {
                null
            } else {
                DockChoice.nearestSelectable(
                    positionPx = if (alongRun) finger.x else finger.y,
                    items =
                        env.entryBounds().map { (key, bounds) ->
                            DockChoiceItem(
                                key = key,
                                startPx = if (alongRun) bounds.left else bounds.top,
                                endPx = if (alongRun) bounds.right else bounds.bottom,
                            )
                        },
                )
            }
        val previous = selectedKey
        selectedKey = next
        if (tickOnSelect && DockChoice.shouldTick(previous, next)) onTick()
    }

    /** How far the finger is beyond the Dock's inner edge (the one away from the screen's), in dp. */
    private fun awayDp(
        env: Environment,
        dock: Rect,
    ): Float {
        val pull = env.pull
        val innerEdge =
            when (pull) {
                DockPullDirection.UP -> -dock.top
                DockPullDirection.DOWN -> dock.bottom
                DockPullDirection.LEFT -> -dock.left
                DockPullDirection.RIGHT -> dock.right
            }
        return (pull.unitX * finger.x + pull.unitY * finger.y - innerEdge) / env.density
    }

    private fun thicknessDp(
        env: Environment,
        dock: Rect,
        alongRun: Boolean,
    ): Float = (if (alongRun) dock.height else dock.width) / env.density
}

/** Where the entry keyed [key] starts along the run in the root now, or null; for the test diagnostics. */
internal fun DockChoiceState.entryX(key: String?): Float? {
    val env = environment
    val bounds = if (env == null || key == null) null else env.entryBounds()[key]
    return bounds?.let { if (env?.pull?.unitY != 0f) it.left else it.top }
}

/** The choice the Dock's tiles and row read; null outside the launcher's Dock, where nothing is ever chosen. */
internal val LocalDockChoice = staticCompositionLocalOf<DockChoiceState?> { null }

/**
 * The marks of a selected tile: it is drawn slightly larger with a ring, and under reduced motion at once instead of
 * growing. The state is read in this composition only to flip a boolean, so a moving selection recomposes just the two
 * tiles it leaves and lands on.
 */
@Composable
internal fun Modifier.dockChoiceMark(
    entryKey: String,
    shape: Shape,
    reducedMotion: Boolean,
): Modifier {
    val choice = LocalDockChoice.current ?: return this
    val picked by remember(choice, entryKey) { derivedStateOf { choice.selectedKey == entryKey } }
    val scale by animateFloatAsState(
        targetValue = if (picked) DockMotionConstants.CHOOSE_SELECTED_SCALE else 1f,
        animationSpec = RiffleMotion.snappy(reducedMotion),
        label = "dock-choice-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.then(
        if (picked) {
            Modifier.border(CHOICE_RING_DP.dp, MaterialTheme.colorScheme.primary, shape)
        } else {
            Modifier
        },
    )
}

/**
 * The selected item's name in a small label just above the Dock (beyond it, on the side away from its edge), drawn
 * while choosing. A visual echo of what the item itself says when focused, so it is hidden from accessibility (it would
 * otherwise be announced again at every move); tests read [DockChoiceLabelKey]. Instant under reduced motion and
 * otherwise too: it only appears and changes text.
 */
@Composable
internal fun DockChoiceLabel(
    choice: DockChoiceState,
    labelFor: (String) -> String?,
    pull: DockPullDirection,
    /** Sized to the Dock (the caller's `matchParentSize`): the label aligns to its inner edge. */
    modifier: Modifier,
) {
    val key = choice.selectedKey
    val text = if (choice.choosing && key != null) labelFor(key) else null
    if (text == null) return
    val alignment =
        when (pull) {
            DockPullDirection.UP -> Alignment.TopCenter
            DockPullDirection.DOWN -> Alignment.BottomCenter
            DockPullDirection.LEFT -> Alignment.CenterStart
            DockPullDirection.RIGHT -> Alignment.CenterEnd
        }
    Box(modifier = modifier, contentAlignment = alignment) {
        GlassSurface(
            modifier =
                Modifier
                    .wrapContentSize(unbounded = true)
                    // Out of the Dock, toward the content: by its own size and a gap.
                    .graphicsLayer {
                        val gap = CHOICE_LABEL_GAP_DP.dp.toPx()
                        translationX = pull.unitX * (size.width + gap)
                        translationY = pull.unitY * (size.height + gap)
                    }
                    .clearAndSetSemantics {
                        testTag = DOCK_CHOICE_LABEL_TEST_TAG
                        choiceLabel = text
                    },
            weight = GlassWeight.Thin,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                maxLines = 1,
            )
        }
    }
}

/** What the choice's label says, for tests (the label itself is hidden from accessibility). */
internal val DockChoiceLabelKey = SemanticsPropertyKey<String>("DockChoiceLabel")
private var SemanticsPropertyReceiver.choiceLabel by DockChoiceLabelKey

/**
 * Keeps [choice] current while it is choosing: the selection is looked at again whenever an entry moves (the row
 * scrolled under a still finger), and frames only run while the finger holds near an end of the row (the edge scroll),
 * so a finger that rests elsewhere keeps the app idle. The frame clock is the platform's, which a test's clock drives
 * too.
 */
@Composable
internal fun DockChoiceFrames(choice: DockChoiceState) {
    LaunchedEffect(choice, choice.choosing) {
        if (!choice.choosing) return@LaunchedEffect
        launch { snapshotFlow { choice.entryPlaces() }.collect { choice.refresh() } }
        snapshotFlow { choice.edgeDirection }.collectLatest { direction ->
            if (direction == 0) return@collectLatest
            var last = -1L
            while (true) {
                val now = withFrameNanos { it }
                if (last >= 0 && !choice.frame((now - last) / NANOS_PER_SECOND)) break
                last = now
            }
        }
    }
}

/** Registers the row's viewport and its scroll with [choice] while the row is composed. */
@Composable
internal fun DockChoiceRowRegistration(
    choice: DockChoiceState?,
    scrollBy: (Float) -> Float,
) {
    if (choice == null) return
    val latest = androidx.compose.runtime.rememberUpdatedState(scrollBy)
    DisposableEffect(choice) {
        choice.scrollRowBy = { delta -> latest.value(delta) }
        onDispose {
            choice.scrollRowBy = null
            choice.rowViewport = null
        }
    }
}

private const val CHOICE_RING_DP = 3
private const val CHOICE_LABEL_GAP_DP = 8
private const val NANOS_PER_SECOND = 1_000_000_000f
internal const val DOCK_CHOICE_LABEL_TEST_TAG = "dock-choice-label"
