package com.riffle.app.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.stream.PageQuickActions
import com.riffle.app.launcher.stream.StreamIcon
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.QuickAction
import com.riffle.core.domain.launcher.stream.QuickChipFit

/** The test tag of a row of quick actions, wherever it sits (the Dock's body, or under the stack). */
internal const val QUICK_ACTIONS_ROW_TEST_TAG = "quick-actions-row"

/** The test tag of the strip of quick actions under the stack (a side Dock): the glass the row is drawn on. */
internal const val QUICK_ACTIONS_STRIP_TEST_TAG = "quick-actions-strip"

/** The test tag of one quick action's chip, by the shortcut's id. */
internal fun quickActionChipTag(id: String): String = "quick-action:$id"

private val ChipTarget = 48.dp

/** A scrolled chip less than this much in view is not drawn: no half chip at any strength at the row's edge. */
private const val CHIP_VANISH_BELOW = 0.5f

/** The strip's one height: a chip's target and the row's own padding. Fixed, so the room it takes can be reserved. */
internal val QuickActionsStripHeight = ChipTarget + RiffleSpacing.xxs * 2
private val ChipIconSize = 24.dp

/**
 * A page's quick actions as one row of chips (ADR 0003, Amendment 3): each the shortcut's small icon and its short
 * label, a 48 dp target; the row scrolls sideways when they do not fit and a chip at a clipped end fades by how much
 * of it shows ([dockEdgeFade]). A tap launches the shortcut. It draws no surface of its own: the Dock's body or the
 * strip
 * under the stack is its glass. A labelled group ("Quick actions for <app>") of buttons, each named by its shortcut.
 */
@Composable
internal fun QuickActionsRow(
    quick: PageQuickActions,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.stream_quick_actions_for, quick.appLabel)
    val viewport = remember { DockRunViewport(runsHorizontally = true) }
    val scroll = rememberScrollState()
    CompositionLocalProvider(
        LocalDockRunViewport provides viewport,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxWidth()
                    .testTag(QUICK_ACTIONS_ROW_TEST_TAG)
                    .semantics {
                        isTraversalGroup = true
                        paneTitle = title
                    }
                    .onGloballyPositioned { coordinates ->
                        val bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                        if (viewport.bounds != bounds) viewport.bounds = bounds
                    },
        ) {
            // The chips fit whole when they can (labels give up their ends before a chip is cut by the edge), and the
            // row scrolls, a chip at the cut fading out, only when even that would leave them too small.
            val rowPadding = RiffleSpacing.s
            val available = with(LocalDensity.current) { (maxWidth - rowPadding * 2).roundToPx() }
            Row(
                modifier =
                    Modifier
                        .horizontalScroll(scroll)
                        .padding(horizontal = rowPadding, vertical = RiffleSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FittedChips(availablePx = available) {
                    quick.actions.forEach { action ->
                        key(action.shortcut.id.value) { QuickActionChip(action, quick) }
                    }
                }
            }
        }
    }
}

/** Lays the chips in a row at the widths [QuickChipFit] gives them for [availablePx]. */
@Composable
private fun FittedChips(
    availablePx: Int,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val gap = RiffleSpacing.xs.roundToPx()
        val natural = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight.coerceAtMost(INTRINSIC_HEIGHT)) }
        val widths = QuickChipFit.fitted(natural, gap, availablePx, MinChipWidth.roundToPx()) ?: natural
        val placeables =
            measurables.mapIndexed { i, m ->
                m.measure(
                    Constraints(minWidth = 0, maxWidth = widths[i], minHeight = 0, maxHeight = constraints.maxHeight),
                )
            }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

/** A chip is never fitted narrower than this (its icon and the start of its label); the row scrolls first. */
private val MinChipWidth = 88.dp

private const val INTRINSIC_HEIGHT = 10_000

@Composable
private fun QuickActionChip(
    action: QuickAction,
    quick: PageQuickActions,
) {
    Box(
        modifier =
            Modifier
                .dockEdgeFade(vanishBelow = CHIP_VANISH_BELOW)
                .heightIn(min = ChipTarget)
                .testTag(quickActionChipTag(action.id))
                .clickable(role = Role.Button) { quick.onLaunch(action) }
                .padding(vertical = RiffleSpacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RiffleShapes.full)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RiffleShapes.full)
                    .padding(
                        start = RiffleSpacing.s,
                        end = RiffleSpacing.m,
                        top = RiffleSpacing.xs,
                        bottom = RiffleSpacing.xs,
                    ),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreamIcon(action.icon, quick.env, ChipIconSize, shape = RiffleShapes.full)
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The quick actions under the stack (a side Dock, board QA-U1): one Thin glass strip as wide as the cards, the row
 * of chips on it. The strip's own box is its bounds, for what lies under it.
 */
@Composable
internal fun QuickActionsStrip(
    quick: PageQuickActions,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier =
            modifier.fillMaxWidth().height(
                QuickActionsStripHeight,
            ).reportsCardRect().testTag(QUICK_ACTIONS_STRIP_TEST_TAG),
        weight = GlassWeight.Thin,
        shape = RiffleShapes.large,
        tint = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        QuickActionsRow(quick)
    }
}
