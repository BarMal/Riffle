package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.stream.CardActionControls
import com.riffle.app.launcher.stream.LocalCardActionFieldColor
import com.riffle.app.launcher.stream.OpenCardActions
import com.riffle.app.launcher.stream.PageQuickActions
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

internal const val DOCK_CARD_ACTIONS_TEST_TAG = "dock-card-actions"

/** The test tag of the Dock's row while it holds the page's quick actions. */
internal const val DOCK_QUICK_ACTIONS_TEST_TAG = "dock-quick-actions"

/** The semantics property of the opacity the Dock's row is drawn at (a test hook, read with the tree). */
internal val DockHeaderAlphaKey = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("DockHeaderAlpha")

/**
 * What the Dock's body needs to hold its row (ADR 0003, Amendment 1, "Stream: browse, then open", and Amendment 3,
 * "Quick actions"): the open card's actions (null while none is open), the quick actions of the page in front (null
 * when it has none), and where to report the row's height to as it grows and goes, so Home's room for the Dock can
 * leave it out. One row holds one of the two: an open card's own actions take it and the quick actions leave until the
 * card closes.
 */
@Stable
internal class DockHeaderState(
    val actions: OpenCardActions?,
    val quickActions: PageQuickActions?,
    val reducedMotion: Boolean,
    val onHeightChanged: (Int) -> Unit,
)

/** What the row holds now: the open card's actions or the page's quick actions, with the identity it fades on. */
private sealed interface HeaderContent {
    val identity: Any

    data class Card(val actions: OpenCardActions) : HeaderContent {
        override val identity: Any get() = "card:${actions.content.id.value}"
    }

    data class Quick(val quick: PageQuickActions) : HeaderContent {
        override val identity: Any get() = "quick:${quick.pageKey}"
    }
}

private fun DockHeaderState.wanted(): HeaderContent? =
    when {
        actions != null -> actions.takeIf { it.actions.isNotEmpty() }?.let(HeaderContent::Card)
        quickActions != null && quickActions.actions.isNotEmpty() -> HeaderContent.Quick(quickActions)
        else -> null
    }

/** The actions the Dock's body holds above (or, on a top Dock, below) its icons; null: it holds none. */
internal val LocalDockHeader = compositionLocalOf<DockHeaderState?> { null }

/**
 * The Dock's one body with the actions row in it: the row sits on the side of the icons that faces into the screen, the
 * body's glass, edge lights and spotlight are drawn once around both, and the pinned icons stay where they are
 * while the body grows away from them. The icons' strip is measured first and the row takes its width.
 */
@Composable
internal fun DockBodyWithHeader(
    header: DockHeaderState,
    headerAtEnd: Boolean,
    modifier: Modifier = Modifier,
    strip: @Composable () -> Unit,
) {
    Layout(
        // The extended body (its row and its icons) hides the grid under it: no app shows at full strength through it.
        modifier = modifier.reportsGridCover(withOpenCard = false, active = header.wanted() != null),
        content = {
            strip()
            DockActionsHeader(header, headerAtEnd)
        },
    ) { measurables, constraints ->
        val stripPlaceable = measurables[0].measure(constraints)
        val headerPlaceable =
            measurables[1].measure(
                Constraints(
                    minWidth = stripPlaceable.width,
                    maxWidth = stripPlaceable.width,
                    minHeight = 0,
                    maxHeight = Constraints.Infinity,
                ),
            )
        val height = stripPlaceable.height + headerPlaceable.height
        layout(stripPlaceable.width, height) {
            if (headerAtEnd) {
                stripPlaceable.place(0, 0)
                headerPlaceable.place(0, stripPlaceable.height)
            } else {
                headerPlaceable.place(0, 0)
                stripPlaceable.place(0, headerPlaceable.height)
            }
        }
    }
}

/**
 * The row itself: the card's actions or the page's quick actions, and a hairline between them and the icons. It grows
 * in the Dock's one motion (its spring at the opening speed, critically damped; a short fade under reduced motion),
 * the controls fading in as the body reaches them. Changing what it holds (a card opens, a page changes) cross-fades
 * the content and eases the height to the new content's on the same spring, so the body is one outline on one motion.
 * The row's own box is its visible part, so its bounds are the room it takes at every frame.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun DockActionsHeader(
    header: DockHeaderState,
    atEnd: Boolean,
) {
    val target = header.wanted()
    // What is drawn: the content stays while the row leaves, so it does not blank before it has gone.
    var shown by remember { mutableStateOf<HeaderContent?>(null) }
    val current = shown
    if (target != null && (current == null || current.identity == target.identity)) shown = target
    val latestTarget by rememberUpdatedState(target)
    val tuning = LocalDockMotionTuning.current
    val spec: AnimationSpec<Float> =
        if (header.reducedMotion) {
            tween(
                RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS,
            )
        } else {
            RiffleMotion.smooth(tuning)
        }
    // The content's own opacity: dips to 0 and back when the row holds something else.
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(target?.identity) {
        val wanted = latestTarget ?: return@LaunchedEffect
        if (shown?.identity != wanted.identity) {
            contentAlpha.animateTo(0f, tween(CROSS_FADE_HALF_MILLIS))
            shown = wanted
            contentAlpha.animateTo(1f, tween(CROSS_FADE_HALF_MILLIS))
        }
    }
    val presence = remember { Animatable(0f) }
    LaunchedEffect(target != null) { presence.animateTo(if (target != null) 1f else 0f, spec) }
    // The height the row eases to: the natural height of what it holds (measured below), or nothing.
    val natural = remember { mutableIntStateOf(0) }
    val height = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { if (latestTarget != null) natural.intValue else 0 }
            .collectLatest { toPx ->
                height.animateTo(toPx.toFloat(), spec)
                if (toPx == 0 && latestTarget == null) shown = null
            }
    }
    val title = stringResource(R.string.stream_card_actions)
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA)
    val fieldColor = MaterialTheme.colorScheme.surface.copy(alpha = FIELD_BACKING_ALPHA)
    val content = shown
    val visible = content != null && (target != null || presence.value > 0f)
    val tag =
        when {
            !visible -> null
            content is HeaderContent.Quick -> DOCK_QUICK_ACTIONS_TEST_TAG
            else -> DOCK_CARD_ACTIONS_TEST_TAG
        }
    Layout(
        modifier =
            Modifier
                .onSizeChanged { header.onHeightChanged(it.height) }
                .then(if (tag != null) Modifier.reportsCardRect() else Modifier)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                .semantics {
                    isTraversalGroup = true
                    if (content !is HeaderContent.Quick) paneTitle = title
                }
                .clipToBounds()
                .graphicsLayer { alpha = presence.value * contentAlpha.value }
                .semantics { this[DockHeaderAlphaKey] = presence.value * contentAlpha.value },
        content = {
            if (visible && content != null) {
                CompositionLocalProvider(
                    LocalCardActionFieldColor provides fieldColor,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (atEnd) Hairline(divider)
                        when (content) {
                            is HeaderContent.Card ->
                                CardActionControls(
                                    content = content.actions.content,
                                    actions = content.actions.actions,
                                    callbacks = content.actions.callbacks,
                                    onToggleQueue = content.actions.onToggleQueue,
                                    modifier =
                                        Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xxs),
                                )
                            is HeaderContent.Quick -> QuickActionsRow(content.quick)
                        }
                        if (!atEnd) Hairline(divider)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val open = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeable = measurables.firstOrNull()?.measure(open)
        val naturalPx = placeable?.height ?: 0
        if (natural.intValue != naturalPx) natural.intValue = naturalPx
        val heightPx = height.value.roundToInt().coerceAtLeast(0)
        layout(constraints.maxWidth, heightPx) {
            // The row slides in from the icons' far side: the part nearest the icons shows first as the body grows.
            placeable?.place(0, if (atEnd) 0 else heightPx - naturalPx)
        }
    }
}

@Composable
private fun Hairline(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color))
}

private const val HAIRLINE_ALPHA = 0.14f
private const val FIELD_BACKING_ALPHA = 0.5f

/** Half of the cross-fade when the row holds something else: out, then in. */
private const val CROSS_FADE_HALF_MILLIS = 90

/** [reservedExtent] with the open card's actions row in it: the room the Stream and its card leave for the Dock. */
@Composable
internal fun HomeDockHostState.reservedExtentWithActions(
    layout: HomeLayout,
    position: DockPosition,
): Dp {
    val measuredPx = areaExtentPx.intValue
    val measuredOnThisEdge = measuredEdge.value == null || measuredEdge.value == position
    return if (measuredPx == UNMEASURED_DOCK_EXTENT_PX || !measuredOnThisEdge) {
        reservedExtent(layout, position)
    } else {
        with(LocalDensity.current) { measuredPx.toDp() }
    }
}
