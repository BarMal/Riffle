package com.riffle.app.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.glance.GlanceWidget
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A widget stack on Home (ADR 0003, Amendment 14): several widgets of one span in one slot, one shown at a time.
 *
 * - **Browse.** A swipe across the pager's axis (up and down while the pages scroll sideways, and the other way
 *   round) moves one widget on; the widgets slide on the Page spring, or jump under reduced motion. A swipe on the
 *   page-dots strip (a 48 dp target on the stack's trailing or bottom edge) always switches. A swipe anywhere on the
 *   stack switches only while the shown widget is one of Riffle's own: a platform widget may scroll along the same
 *   axis, so it keeps the swipe (see `gestures.md`, "Widget stacks").
 * - **One slot.** Move, resize, menu and removal act on the whole stack; the stack's chrome is the one a widget has.
 * - **Cost.** Only the shown widget and its two neighbours are composed (so only they hold a host view).
 * - **Frame stability.** Where the stack is between widgets and how visible its dots are are read in layout and draw
 *   lambdas, never by composition.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
internal fun HomeWidgetStack(
    widget: WidgetItem,
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
    contextSurface: ShortcutContextSurface,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    dragState: HomeItemDragState? = null,
    workspaceActions: HomeWorkspaceActions? = null,
    presentation: WidgetStackPresentation = WidgetStackPresentation(),
) {
    CountRecomposition(homeWidgetStackProbeKey(widget.id.value))
    val members = widget.members
    val count = members.size
    val shown = widget.shownIndex.coerceIn(0, count - 1)
    val shownMember = members[shown]
    val acrossPager = !presentation.pagesScrollVertically
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = workspaceActions?.haptics
    val swipe =
        rememberWidgetStackSwipe(
            count = count,
            shown = shown,
            reducedMotion = presentation.reducedMotion,
            onShown = { index ->
                haptics?.perform(LauncherHapticEvent.DETENT)
                onAction(LauncherShellAction.ShowStackedWidget(widget.id, index))
            },
        )
    val isContextMenuExpanded = remember(widget.id) { mutableStateOf(false) }
    val editing = rememberUpdatedState(isEditing)
    val interactive =
        remember(workspaceActions) { { !editing.value && workspaceActions?.currentDragSession() == null } }
    val ownWidgetShown = shownMember.glance != null
    val stateText = stringResource(R.string.widget_stack_state, shown + 1, count, shownMember.label)
    val nextLabel = stringResource(R.string.widget_stack_next)
    val previousLabel = stringResource(R.string.widget_stack_previous)
    val editLabel = stringResource(R.string.menu_edit_stack)
    val span = widget.placement?.span ?: GridSpan()

    val dragModifier =
        if (dragState != null && workspaceActions != null) {
            Modifier.homeItemDrag(
                enabled = true,
                item = widget,
                dragState = dragState,
                actions = workspaceActions,
                onStationaryLongPress = { isContextMenuExpanded.value = true },
                onMenuDismiss = { isContextMenuExpanded.value = false },
            )
        } else {
            Modifier
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(HOME_WIDGET_STACK_TEST_TAG)
                .clipToBounds()
                .semantics {
                    contentDescription = shownMember.label
                    stateDescription = stateText
                    customActions =
                        listOf(
                            CustomAccessibilityAction(nextLabel) { swipe.step(1) },
                            CustomAccessibilityAction(previousLabel) { swipe.step(-1) },
                            CustomAccessibilityAction(editLabel) {
                                onAction(LauncherShellAction.OpenWidgetStackEditor(widget.id))
                                true
                            },
                        )
                }
                // Riffle's own widgets never scroll: a swipe anywhere on them switches. A platform widget may.
                .widgetStackSwipe(
                    swipe = swipe,
                    vertical = acrossPager,
                    reverse = !acrossPager && rtl,
                    enabled = ownWidgetShown && !isEditing,
                ),
    ) {
        // The shown widget and its neighbours; the rest hold no view.
        for (index in max(0, shown - 1)..minOf(count - 1, shown + 1)) {
            key(index) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .stackSlot(swipe = swipe, index = index, vertical = acrossPager)
                            .then(if (index == shown) Modifier else Modifier.clearAndSetSemantics { }),
                ) {
                    StackMemberBody(
                        widget = widget,
                        member = members[index],
                        span = span,
                        widgetViewFactory = widgetViewFactory,
                        interactive = interactive,
                        // Riffle's own widget takes its taps itself; the long press is the stack's drag and menu.
                        modifier = if (ownWidgetShown && !isEditing && index == shown) dragModifier else Modifier,
                    )
                }
            }
        }
        // A platform widget is under Home's gesture layer (as a lone one is); an edit covers either kind.
        if (!ownWidgetShown || isEditing) {
            WidgetGestureLayer(
                widget = widget,
                dragState = dragState,
                workspaceActions = workspaceActions,
                onStationaryLongPress = { isContextMenuExpanded.value = true },
                onMenuDismiss = { isContextMenuExpanded.value = false },
            )
        }
        if (isEditing) {
            WidgetEditHandles(
                widget = widget,
                grid = dragState?.grid,
                pageItems = dragState?.pageItems ?: listOf(widget),
                contextSurface = contextSurface,
                onAction = onAction,
            )
        }
        WidgetStackDotsStrip(
            swipe = swipe,
            count = count,
            vertical = acrossPager,
            stateText = stateText,
            nextLabel = nextLabel,
            // The strip takes a swipe that starts on it for a platform widget; over Riffle's own widget its dots are
            // only drawn and a touch goes through to the widget.
            takesTouches = !ownWidgetShown && !isEditing,
            reverse = !acrossPager && rtl,
            dragModifier = if (!isEditing) dragModifier else Modifier,
            modifier = Modifier.align(if (acrossPager) Alignment.CenterEnd else Alignment.BottomCenter),
        )
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                widgetPlaceholderContextMenuItems(
                    widget = widget,
                    grid = dragState?.grid,
                    pageItems = dragState?.pageItems ?: listOf(widget),
                    contextSurface = contextSurface,
                    text = rememberSettingsText(),
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = onAction,
        )
    }
}

/** Places a member where [swipe] has it: [index] widgets from the position, along the axis, in the layout phase. */
private fun Modifier.stackSlot(
    swipe: WidgetStackSwipe,
    index: Int,
    vertical: Boolean,
): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val extent = if (vertical) placeable.height else placeable.width
            swipe.extentPx = extent.toFloat()
            val shift = ((index - swipe.position.value) * extent).roundToInt()
            if (vertical) placeable.placeRelative(0, shift) else placeable.placeRelative(shift, 0)
        }
    }

/** One widget of the stack, drawn as it would be on its own (a platform widget in its glass frame, or Riffle's own). */
@Composable
private fun StackMemberBody(
    widget: WidgetItem,
    member: StackedWidget,
    span: GridSpan,
    widgetViewFactory: HomeWidgetViewFactory,
    interactive: () -> Boolean,
    modifier: Modifier,
) {
    val kind: GlanceWidgetKind? = member.glance
    if (kind != null) {
        GlanceWidget(
            kind = kind,
            span = span,
            interactive = interactive,
            modifier = Modifier.fillMaxSize().then(modifier),
        )
    } else {
        HostedWidgetFrame(
            widget =
                widget.copy(
                    appWidgetId = member.appWidgetId,
                    label = member.label,
                    resizeConstraints = member.resizeConstraints,
                    glance = null,
                    stack = emptyList(),
                    shownIndex = 0,
                ),
            widgetViewFactory = widgetViewFactory,
        )
    }
}

/**
 * The stack's page dots on its trailing edge (bottom edge when the pages scroll up and down): one per widget, the shown
 * one larger and brighter, fading to an idle level when the stack is not in use. The strip is a 48 dp wide target.
 * Dots and their fade are drawn from [swipe]'s animated values in the draw phase.
 */
@Composable
@Suppress("LongParameterList")
private fun WidgetStackDotsStrip(
    swipe: WidgetStackSwipe,
    count: Int,
    vertical: Boolean,
    stateText: String,
    nextLabel: String,
    takesTouches: Boolean,
    reverse: Boolean,
    dragModifier: Modifier,
    modifier: Modifier,
) {
    val description = stringResource(R.string.widget_stack_dots_description)
    val active = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.surfaceContainerHighest
    val dotSpan = (DOT_DIAMETER_DP + DOT_GAP_DP) * count
    Box(
        modifier =
            modifier
                .then(
                    if (vertical) {
                        Modifier.width(STRIP_SIZE_DP.dp).fillMaxHeight()
                    } else {
                        Modifier.height(STRIP_SIZE_DP.dp).fillMaxWidth()
                    },
                )
                .testTag(HOME_WIDGET_STACK_DOTS_TEST_TAG)
                .semantics {
                    contentDescription = description
                    stateDescription = stateText
                    onClick(label = nextLabel) { swipe.step(1) }
                }
                .then(
                    if (takesTouches) {
                        Modifier.widgetStackSwipe(swipe, vertical, reverse).then(dragModifier)
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                if (vertical) {
                    Modifier.width(DOT_RING_DP.dp).height(dotSpan.dp)
                } else {
                    Modifier.height(DOT_RING_DP.dp).width(dotSpan.dp)
                },
        ) {
            val step = (DOT_DIAMETER_DP + DOT_GAP_DP).dp.toPx()
            val fade = swipe.dotsAlpha.value
            val position = swipe.position.value
            for (index in 0 until count) {
                val near = (1f - abs(position - index)).coerceIn(0f, 1f)
                val centre = step * index + step / 2f
                val at = if (vertical) Offset(size.width / 2f, centre) else Offset(centre, size.height / 2f)
                val radius = (DOT_DIAMETER_DP / 2f + DOT_GROWTH_DP * near).dp.toPx()
                drawCircle(color = ring, radius = radius + DOT_RING_WIDTH_DP.dp.toPx(), center = at, alpha = fade)
                drawCircle(
                    color = active,
                    radius = radius,
                    center = at,
                    alpha = fade * (DOT_BASE_ALPHA + (1f - DOT_BASE_ALPHA) * near),
                )
            }
        }
    }
}

internal const val HOME_WIDGET_STACK_TEST_TAG = "home-widget-stack"
internal const val HOME_WIDGET_STACK_DOTS_TEST_TAG = "home-widget-stack-dots"

/** The dots strip: at least a 48 dp target along its short side. */
internal const val STRIP_SIZE_DP = 48
private const val DOT_DIAMETER_DP = 6
private const val DOT_GAP_DP = 6
private const val DOT_GROWTH_DP = 1.5f
private const val DOT_RING_DP = 16
private const val DOT_RING_WIDTH_DP = 1.5f
private const val DOT_BASE_ALPHA = 0.55f
