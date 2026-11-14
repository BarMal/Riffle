package com.riffle.app.launcher.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.AppSearchField
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SearchResultList
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.dpBounds
import com.riffle.app.launcher.launcherPanelContentColor
import com.riffle.app.launcher.launcherPanelSurfaceColor
import com.riffle.app.launcher.library.LibraryState
import com.riffle.app.launcher.library.toGridResult
import com.riffle.app.launcher.searchEmptyText
import com.riffle.app.launcher.searchWebPreview
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.dock.DockSearchMotion
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.stream.StreamColumn
import com.riffle.core.domain.launcher.stream.StreamColumnRect

/**
 * Search, opened from the Dock (ADR 0003, Amendment 1, "Home, Library and search"): the field sits on the Dock's edge
 * within the thumb's reach and the results grow up from it, the best match nearest it. Both are Thick glass (dense text
 * has to read over any wallpaper). The keyboard's room is the caller's: it lays this out above the Dock, which stands
 * above the keyboard, so the field and the results stay clear of it.
 *
 * It rises from the Dock's edge on the Dock's calm spring at the Dock's opening speed ([tuning]), and closing runs the
 * same motion back before the shell is told ([onClose]); under reduced motion it is the 80 ms fade. A tap on the room
 * around it, Back with an empty field and the "Close search" action close it; Back with text
 * clears the text first. Launching a result is the caller's ([context]).
 */
@Composable
@Suppress("LongParameterList")
internal fun DockSearchSurface(
    state: LibraryState,
    context: AppListContext,
    reducedMotion: Boolean,
    direction: DockPullDirection,
    tuning: DockMotionTuning,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The room the panel keeps clear of (the Dock's reserved room and the system bars and keyboard), folded: the panel
     * stands in what is left, at the bottom, centred. Unfolded the panel's place is [column] instead.
     */
    contentPadding: PaddingValues = PaddingValues(),
    /**
     * Unfolded (ADR 0003, Amendment 2): where the Stream's column stands at rest, in dp in this surface's coordinates
     * (`StreamColumn.rect`: the same width, the same side against the Dock's inner edge, its bottom at the Dock's
     * lower end and lifted above the keyboard). The field and the results stand in exactly that rectangle, the field
     * at its bottom and the results growing up from it. Null folded.
     */
    column: (() -> StreamColumnRect?)? = null,
) {
    val motion = rememberDockSearchMotion(reducedMotion, tuning, onClosed = onClose)
    val onAction = context.onAction
    val closeLabel = stringResource(R.string.dock_search_close_action)

    BackHandler(enabled = state.isSearching) { onAction(LauncherShellAction.SearchQueryChanged("")) }
    BackHandler(enabled = !state.isSearching) { motion.close() }

    val frameAlpha = { DockSearchMotion.frame(motion.progress, reducedMotion).alpha }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(DOCK_SEARCH_SURFACE_TEST_TAG)
                .semantics {
                    dockSearchProgress = motion.progress
                    dockSearchAlpha = frameAlpha()
                },
    ) {
        // The room around the field and the results belongs to search, all the way to the screen's edges (the system
        // bars and the keyboard's room included): a press there closes it instead of reaching Home.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .testTag(DOCK_SEARCH_SCRIM_TEST_TAG)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = closeLabel,
                        onClick = motion::close,
                    ),
        )
        val panelModifier =
            if (column == null) {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .widthIn(max = SEARCH_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(RiffleSpacing.s)
            } else {
                // The same rectangle the Stream's cards stand in; its cards keep the page's own inset from its edges.
                Modifier
                    .dpBounds(column)
                    .testTag(DOCK_SEARCH_COLUMN_TEST_TAG)
                    .padding(StreamColumn.PAGE_INSET_DP.dp)
            }
        Column(
            modifier =
                panelModifier
                    .graphicsLayer {
                        val frame = DockSearchMotion.frame(motion.progress, reducedMotion)
                        alpha = frame.alpha
                        scaleX = frame.scale
                        scaleY = frame.scale
                        // Toward the Dock's edge, which is behind the pull direction (up from a bottom Dock).
                        translationX = -direction.unitX * frame.riseDp * density
                        translationY = -direction.unitY * frame.riseDp * density
                        transformOrigin = direction.dockEdgeOrigin()
                    },
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (state.isSearching) {
                SearchResultList(
                    results = state.hits.map { hit -> hit.toGridResult() },
                    appListContext = context,
                    emptyText = searchEmptyText(state.query, state.filters),
                    presentation = state.searchPresentation,
                    gridColumns = state.searchGridColumns,
                    webPreview = searchWebPreview(state.query),
                    growsUp = true,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.height(RiffleSpacing.s))
            }
            DockSearchField(state = state, onAction = onAction, closeLabel = closeLabel, onClose = motion::close)
        }
    }
}

/** The field, on its own Thick glass: Enter launches the top hit. It takes focus and raises the keyboard on opening. */
@Composable
private fun DockSearchField(
    state: LibraryState,
    onAction: (LauncherShellAction) -> Unit,
    closeLabel: String,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.focusSearch) {
        if (state.focusSearch) {
            // Whatever the panel's opacity: a field fading in takes focus and the keyboard at once.
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    val latestHits by rememberUpdatedState(state.hits)
    GlassSurface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(closeLabel) {
                                onClose()
                                true
                            },
                        )
                },
        // The field is dense text: Thick, so it reads over any wallpaper.
        weight = GlassWeight.Thick,
        shape = RiffleShapes.extraLarge,
        tint = launcherPanelSurfaceColor(),
        contentColor = launcherPanelContentColor(),
    ) {
        AppSearchField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(RiffleSpacing.s)
                    .focusRequester(focusRequester)
                    .testTag(DOCK_SEARCH_FIELD_TEST_TAG),
            query = state.query,
            onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
            label = stringResource(R.string.library_search_label),
            onSearch = { latestHits.firstOrNull()?.toGridResult()?.action?.let(onAction) },
        )
    }
}

/**
 * The surface's progress: 0 closed to 1 open, driven by the frame clock, and the way out. [close] runs the motion back
 * and then tells the caller; a second request while it runs changes nothing.
 */
internal class DockSearchMotionState {
    var progress: Float by mutableFloatStateOf(0f)
        internal set

    /** Where progress is heading: 1 while it opens, 0 once a close was asked for. */
    internal var target: Float by mutableFloatStateOf(1f)
        private set

    fun close() {
        target = 0f
    }
}

@Composable
private fun rememberDockSearchMotion(
    reducedMotion: Boolean,
    tuning: DockMotionTuning,
    onClosed: () -> Unit,
): DockSearchMotionState {
    val state = remember { DockSearchMotionState() }
    val latestOnClosed = rememberUpdatedState(onClosed)
    val latestTuning = rememberUpdatedState(tuning)
    LaunchedEffect(state.target, reducedMotion) {
        val from = state.progress
        val to = state.target
        val start = withFrameNanos { it }
        var elapsed = 0f
        while (!hasArrived(reducedMotion, from, to, elapsed, latestTuning.value.omega)) {
            elapsed = (withFrameNanos { it } - start) / NANOS_PER_SECOND
            state.progress =
                if (reducedMotion) {
                    DockSearchMotion.reducedProgress(from, to, elapsed)
                } else {
                    DockSearchMotion.progress(from, to, elapsed, latestTuning.value.omega)
                }
        }
        state.progress = to
        if (to == 0f) latestOnClosed.value()
    }
    return state
}

private fun hasArrived(
    reducedMotion: Boolean,
    from: Float,
    to: Float,
    elapsed: Float,
    omega: Float,
): Boolean =
    if (reducedMotion) {
        DockSearchMotion.reducedIsArrived(elapsed)
    } else {
        DockSearchMotion.isArrived(from, to, elapsed, omega)
    }

/** The point the surface grows from: the middle of the Dock's edge, as a fraction of the surface. */
private fun DockPullDirection.dockEdgeOrigin(): TransformOrigin =
    TransformOrigin(
        pivotFractionX =
            if (unitX > 0f) {
                0f
            } else if (unitX < 0f) {
                1f
            } else {
                0.5f
            },
        pivotFractionY =
            if (unitY < 0f) {
                1f
            } else if (unitY > 0f) {
                0f
            } else {
                0.5f
            },
    )

private val SEARCH_MAX_WIDTH = 840.dp
private const val NANOS_PER_SECOND = 1_000_000_000f

/** How far the surface has opened (0 to 1), for a test to watch the motion by; not read by anything else. */
internal val DockSearchProgressKey = SemanticsPropertyKey<Float>("DockSearchProgress")
private var SemanticsPropertyReceiver.dockSearchProgress by DockSearchProgressKey

/** The surface's opacity as drawn (0 to 1; the spring's alpha), for a test to read at rest; not read by anything else. */
internal val DockSearchAlphaKey = SemanticsPropertyKey<Float>("DockSearchAlpha")
private var SemanticsPropertyReceiver.dockSearchAlpha by DockSearchAlphaKey

internal const val DOCK_SEARCH_SURFACE_TEST_TAG = "dock-search-surface"

/** The press catcher over the whole window, and the rectangle unfolded search stands in (the Stream column's). */
internal const val DOCK_SEARCH_SCRIM_TEST_TAG = "dock-search-scrim"
internal const val DOCK_SEARCH_COLUMN_TEST_TAG = "dock-search-column"
internal const val DOCK_SEARCH_FIELD_TEST_TAG = "dock-search-field"
