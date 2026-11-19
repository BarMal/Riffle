package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
import com.riffle.core.domain.launcher.dock.DockScrub
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
@Suppress("TooManyFunctions")
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
        /** The scrub's pages: which entries stand for a page, and which one is the open page's (ringed). */
        val scrubPages: (Collection<String>) -> DockScrubPages = { keys ->
            DockScrubPages(keys.filter(DockChoice::isSelectable).toSet(), null)
        },
    )

    /** What a scrub may land on: [selectable] entries (each stands for a page) and the [ringed] one now. */
    class DockScrubPages(
        val selectable: Set<String>,
        val ringed: String?,
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

    /**
     * Whether a hold on the Dock's contextual row has armed the scrub (the Stream shows; the tile under the finger is
     * the current page and wears the choice mark). Not choosing: the selection only follows the finger, nothing opens
     * on the lift.
     */
    var scrubbing: Boolean by mutableStateOf(false)
        private set

    /** Whether the armed finger has slid past touch slop: the scrub proper, which scrolls the row at an end. */
    var sliding: Boolean by mutableStateOf(false)
        private set

    /**
     * How many times the open menus have been told to close: the hold's menu is closed when the finger moves past
     * slop (the scrub, a reorder drag). A menu closes when this changes while it is open.
     */
    var menuDismissals: Int by mutableStateOf(0)
        private set

    /** Closes the menu the hold opened: the finger moved. */
    fun dismissMenus() {
        menuDismissals += 1
    }

    private var scrubbedKey: String? = null

    /**
     * Whether the ring arriving on [key] is the scrub's own landing (taken once): the row then keeps where the
     * finger or its fling put it, rather than scrolling back to show a tile the finger was just on.
     */
    fun consumeScrubbed(key: String): Boolean = (scrubbedKey == key).also { if (it) scrubbedKey = null }

    /** Called with the key of the tile the scrub entered, after its tick: the Stream shows that tile's page. */
    var onScrubTo: (String) -> Unit = {}

    /** Whether the row has tiles hidden toward its start (-1) or its end (+1); set by the row. */
    var canScrollRowTo: ((Int) -> Boolean)? = null

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
            if (scrubbing && !sliding) return 0
            val alongRun = env.pull.unitY != 0f
            if (scrubbing) {
                val canScroll = canScrollRowTo ?: return 0
                return DockScrub.edgeDirection(
                    fingerPx = if (alongRun) finger.x else finger.y,
                    viewportStartPx = if (alongRun) viewport.left else viewport.top,
                    viewportEndPx = if (alongRun) viewport.right else viewport.bottom,
                    zonePx = DockScrub.EDGE_ZONE_DP * env.density,
                    canScrollToStart = canScroll(-1),
                    canScrollToEnd = canScroll(1),
                )
            }
            return DockChoice.edgeDirection(
                fingerPx = if (alongRun) finger.x else finger.y,
                viewportStartPx = if (alongRun) viewport.left else viewport.top,
                viewportEndPx = if (alongRun) viewport.right else viewport.bottom,
            )
        }

    private val isChoosingNotCancelling: Boolean get() = (choosing && !cancelling) || scrubbing

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

    /**
     * A hold on the contextual row arms the scrub with the finger at [position] (the gesture's own coordinates): one
     * light tick, and the choice mark on the tile under the finger (which becomes the current page when it is not
     * already). Sliding is what scrubs from here ([startSliding]).
     */
    fun armScrub(position: Offset) {
        val env = environment ?: return
        edgeScrolled = false
        cancelling = false
        sliding = false
        scrubbing = true
        scrubbedKey = null
        finger = env.toRoot(position)
        val ringed = env.scrubPages(env.entryBounds().keys).ringed
        selectedKey = ringed
        reselectScrub(env, tickOnSelect = true, tickOnly = false)
        onTick()
    }

    /** The armed finger moved past touch slop: the hold's menu closes and the scrub slides. */
    fun startSliding() {
        if (!scrubbing || sliding) return
        sliding = true
        dismissMenus()
    }

    /** The finger is at [position] (the gesture's own coordinates). */
    fun moved(
        position: Offset,
        tickOnSelect: Boolean = true,
    ) {
        if (!choosing && !(scrubbing && sliding)) return
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
        if (!choosing && !(scrubbing && sliding)) return
        val env = environment ?: return
        reselect(env, tickOnSelect = true)
    }

    /** The press ended, committed or not. */
    fun end() {
        choosing = false
        scrubbing = false
        sliding = false
        selectedKey = null
        cancelling = false
    }

    private fun reselect(
        env: Environment,
        tickOnSelect: Boolean,
    ) {
        if (scrubbing) {
            reselectScrub(env, tickOnSelect, tickOnly = true)
            return
        }
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

    /** The scrub's selection: the tile the finger is on, with hysteresis; other tiles and gaps are passed over. */
    private fun reselectScrub(
        env: Environment,
        tickOnSelect: Boolean,
        tickOnly: Boolean,
    ) {
        val alongRun = env.pull.unitY != 0f
        val bounds = env.entryBounds()
        val next =
            DockScrub.tileAt(
                positionPx = if (alongRun) finger.x else finger.y,
                items =
                    bounds.map { (key, rect) ->
                        DockChoiceItem(
                            key = key,
                            startPx = if (alongRun) rect.left else rect.top,
                            endPx = if (alongRun) rect.right else rect.bottom,
                        )
                    },
                selectable = env.scrubPages(bounds.keys).selectable,
                currentKey = selectedKey,
                hysteresisPx = DockScrub.HYSTERESIS_DP * env.density,
            )
        val previous = selectedKey
        selectedKey = next
        if (tickOnSelect && DockChoice.shouldTick(previous, next)) {
            // Arming ticks once itself (the hold's haptic): landing on another tile then is not a second tick.
            if (tickOnly) onTick()
            scrubbedKey = next
            next?.let(onScrubTo)
        }
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
 * growing. Draw-phase only: the selection is read inside the graphics layer and the draw, never in composition, so a
 * moving selection (hold to choose, or a slide along the Dock) recomposes no tile.
 */
@Composable
internal fun Modifier.dockChoiceMark(
    entryKey: String,
    shape: Shape,
    reducedMotion: Boolean,
): Modifier {
    val choice = LocalDockChoice.current ?: return this
    val picked = remember(choice, entryKey) { derivedStateOf { choice.selectedKey == entryKey } }
    val scale = remember(choice, entryKey) { Animatable(1f) }
    val latestReduced = rememberUpdatedState(reducedMotion)
    LaunchedEffect(choice, entryKey) {
        snapshotFlow { picked.value }.collect { isPicked ->
            val target = if (isPicked) DockMotionConstants.CHOOSE_SELECTED_SCALE else 1f
            if (latestReduced.value) scale.snapTo(target) else scale.animateTo(target, RiffleMotion.snappy(false))
        }
    }
    val ringWidthPx = with(LocalDensity.current) { CHOICE_RING_DP.dp.toPx() }
    val ringColor = MaterialTheme.colorScheme.primary
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }.drawWithContent {
        drawContent()
        if (picked.value) {
            drawOutline(shape.createOutline(size, layoutDirection, this), ringColor, style = Stroke(ringWidthPx))
        }
    }
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
    val active = choice.choosing || choice.scrubbing
    LaunchedEffect(choice, active) {
        if (!active) return@LaunchedEffect
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
    canScrollTo: (Int) -> Boolean = { false },
) {
    if (choice == null) return
    val latest = rememberUpdatedState(scrollBy)
    val latestCan = rememberUpdatedState(canScrollTo)
    DisposableEffect(choice) {
        choice.scrollRowBy = { delta -> latest.value(delta) }
        choice.canScrollRowTo = { direction -> latestCan.value(direction) }
        onDispose {
            choice.scrollRowBy = null
            choice.canScrollRowTo = null
            choice.rowViewport = null
        }
    }
}

private const val CHOICE_RING_DP = 3
private const val CHOICE_LABEL_GAP_DP = 8
private const val NANOS_PER_SECOND = 1_000_000_000f
internal const val DOCK_CHOICE_LABEL_TEST_TAG = "dock-choice-label"
