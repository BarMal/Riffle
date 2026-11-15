package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout
import kotlin.math.roundToInt

internal const val DOCK_CARD_ACTIONS_TEST_TAG = "dock-card-actions"

/**
 * What the Dock's body needs to hold the open card's actions (ADR 0003, Amendment 1, "Stream: browse, then open"): the
 * card's actions (null while none is open) and where to report the row's height to as it grows and goes, so Home's room
 * for the Dock can leave it out.
 */
@Stable
internal class DockHeaderState(
    val actions: OpenCardActions?,
    val reducedMotion: Boolean,
    val onHeightChanged: (Int) -> Unit,
)

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
        modifier = modifier,
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
 * The row itself: the card's actions and a hairline between them and the icons. It grows in the Dock's one motion (its
 * spring at the opening speed, critically damped; a short fade under reduced motion), the controls fading in as the
 * body reaches them. The row's own box is its visible part, so its bounds are the room it takes at every frame.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
private fun DockActionsHeader(
    header: DockHeaderState,
    atEnd: Boolean,
) {
    val actions = header.actions
    // The content stays while the row leaves, so it does not blank before it has gone.
    var last by remember { mutableStateOf<OpenCardActions?>(null) }
    if (actions != null) last = actions
    val shown = actions ?: last
    val wanted = actions != null && actions.actions.isNotEmpty()
    val tuning = LocalDockMotionTuning.current
    val progress by animateFloatAsState(
        targetValue = if (wanted) 1f else 0f,
        animationSpec =
            if (header.reducedMotion) {
                tween(RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS)
            } else {
                RiffleMotion.smooth(tuning)
            },
        label = "dock-actions-row",
    )
    val title = stringResource(R.string.stream_card_actions)
    val divider = MaterialTheme.colorScheme.onSurface.copy(alpha = HAIRLINE_ALPHA)
    val fieldColor = MaterialTheme.colorScheme.surface.copy(alpha = FIELD_BACKING_ALPHA)
    val visible = shown != null && (wanted || progress > 0f)
    Layout(
        modifier =
            Modifier
                .onSizeChanged { header.onHeightChanged(it.height) }
                .then(if (visible) Modifier.testTag(DOCK_CARD_ACTIONS_TEST_TAG) else Modifier)
                .semantics {
                    isTraversalGroup = true
                    paneTitle = title
                }
                .clipToBounds()
                .graphicsLayer { alpha = progress },
        content = {
            if (visible && shown != null) {
                CompositionLocalProvider(
                    LocalCardActionFieldColor provides fieldColor,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (atEnd) Hairline(divider)
                        CardActionControls(
                            content = shown.content,
                            actions = shown.actions,
                            callbacks = shown.callbacks,
                            onToggleQueue = shown.onToggleQueue,
                            modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xxs),
                        )
                        if (!atEnd) Hairline(divider)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val open = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeable = measurables.firstOrNull()?.measure(open)
        val natural = placeable?.height ?: 0
        val height = (natural * progress).roundToInt()
        layout(constraints.maxWidth, height) {
            // The row slides in from the icons' far side: the part nearest the icons shows first as the body grows.
            placeable?.place(0, if (atEnd) 0 else height - natural)
        }
    }
}

@Composable
private fun Hairline(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color))
}

private const val HAIRLINE_ALPHA = 0.14f
private const val FIELD_BACKING_ALPHA = 0.5f

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
