package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.isHorizontalEdge

@Composable
@Suppress("LongParameterList")
internal fun StandardHomeDockArea(
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    notificationShelfState: DockNotificationShelfState,
    isDockShelfExpanded: Boolean,
    onDockShelfExpandedChange: (Boolean) -> Unit,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    position: DockPosition = DockPosition.BOTTOM,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview? = null,
    isWidgetPickerInteractionActive: Boolean = false,
    dynamicBehaviour: DockDynamicSectionBehaviour = DockDynamicSectionBehaviour.LaunchApp,
) {
    if (!layout.shouldShowDock()) {
        return
    }

    val hasExpandedContent =
        dockHasExpandedContent(
            hasPanel = layout.dock.panel != null,
            notificationShelfState = notificationShelfState,
        )
    // Expandability is the user's per-layout choice; content and edit mode are still what decide
    // whether there is anything to expand into right now.
    val canExpand =
        layout.dock.isExpandable && hasExpandedContent && layout.editMode == HomeEditMode.Browsing
    val showDockShelf = isDockShelfExpanded && canExpand
    val dockInteractions =
        DockInteractions(
            haptics = actions.haptics,
            onFolderOpen = actions.onFolderOpen,
            isShelfExpanded = showDockShelf,
            shelfExpandAffordance = layout.dock.expandAffordance,
            position = position,
            onShelfExpandedChange = onDockShelfExpandedChange.takeIf { canExpand },
            reducedMotion = presentation.reducedMotion,
            homeInsetPolicy = presentation.homeInsetPolicy,
            homeLayout = layout,
            onAction = actions.onAction,
        )
    val margins = layout.settings.grid.margin.centered()
    val runsAlongASide = !position.isHorizontalEdge

    Column(
        modifier =
            Modifier
                .dockAreaExtent(
                    runsAlongASide = runsAlongASide,
                    isShelfOpen = showDockShelf,
                    dock = layout.dock,
                    margins = margins,
                )
                .onSizeChanged { size -> actions.onDockInteractionHeightChanged(size.height) }
                .onGloballyPositioned { coordinates ->
                    actions.onDockBoundsChanged(coordinates.boundsInRoot())
                }
                .padding(
                    start = margins.start.dp,
                    end = margins.end.dp,
                    top = if (runsAlongASide) margins.top.dp else 0.dp,
                    bottom = margins.bottom.dp,
                )
                .dockShelfMotion(dockShelfMotionPolicy(presentation.reducedMotion))
                .dockShelfFrameRatePreference(presentation.motionPerformanceTargetFps)
                // Only claim the swipe-up gesture when the shelf-expand gesture is inactive and no
                // widget-picker drag/tile interaction is in play: this Column draws on top of
                // WidgetPickerSurface in the overlapping dock region, so it must yield the region's
                // touches to the picker's own drag detector whenever it is active.
                // The shelf only claims swipe-up when its affordance is the gesture, so a
                // button-expanded (or non-expandable) dock hands the swipe back to this.
                .dockSwipeUpGestureInput(
                    enabled = !dockInteractions.claimsSwipeUp() && !isWidgetPickerInteractionActive,
                    action = presentation.dockGestures.swipeUp,
                    viewMode = layout.viewMode,
                    onAction = actions.onAction,
                ),
        horizontalAlignment = layout.dock.alignment.toHorizontalAlignment(),
        verticalArrangement = if (runsAlongASide) Arrangement.Center else Arrangement.Top,
    ) {
        DockShelfExpandButton(interactions = dockInteractions)
        Spacer(modifier = Modifier.height(HOME_DOCK_TOP_SPACING_DP.dp))
        Box(modifier = Modifier.testTag(HOME_DOCK_TEST_TAG)) {
            DockOrShelf(
                layout = layout,
                presentation = presentation,
                notificationShelfState = notificationShelfState,
                showDockShelf = showDockShelf,
                appIconLoader = appIconLoader,
                position = position,
                interactions = dockInteractions,
                widgetPickerDockPreview = widgetPickerDockPreview,
                dynamicBehaviour = dynamicBehaviour,
            )
        }
    }
}

/** The dock as it stands: its own strip, or the shelf it grows into. */
@Composable
@Suppress("LongParameterList")
private fun DockOrShelf(
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    notificationShelfState: DockNotificationShelfState,
    showDockShelf: Boolean,
    appIconLoader: AppIconLoader,
    position: DockPosition,
    interactions: DockInteractions,
    widgetPickerDockPreview: WidgetPickerDockPlacementPreview?,
    dynamicBehaviour: DockDynamicSectionBehaviour,
) {
    if (showDockShelf) {
        ExpandedDockSurface(
            dock = layout.dock,
            notificationShelfState = notificationShelfState,
            notificationGroupsByApp = presentation.notificationGroupsByApp,
            appShortcutsByApp = presentation.appShortcutsByApp,
            appIconLoader = appIconLoader,
            widgetViewFactory = presentation.widgetViewFactory,
            position = position,
            interactions = interactions,
        )
    } else {
        Dock(
            dock = layout.dock,
            isEditing = layout.editMode is HomeEditMode.EditingPage,
            notificationGroupsByApp = presentation.notificationGroupsByApp,
            appShortcutsByApp = presentation.appShortcutsByApp,
            appIconLoader = appIconLoader,
            widgetViewFactory = presentation.widgetViewFactory,
            position = position,
            interactions = interactions,
            widgetPickerDockPreview = widgetPickerDockPreview,
            // Only the collapsed dock carries the section. Expanded, the shelf's card row *is* the
            // same section with room to say more, so drawing both would show every entry twice.
            dynamicEntries = notificationShelfState.dynamicEntries(),
            dynamicBehaviour = dynamicBehaviour,
        )
    }
}

/**
 * How much of the screen the dock's area claims.
 *
 * A collapsed side dock reserves a strip exactly as thick as the dock plus its margins, and fills
 * the height it was given. Left to wrap its content it would take whatever width its container
 * offered and leave the workspace the remainder, which is the wrong way round: the reservation is
 * what the grid gave up a column for, so it has to be the known quantity.
 *
 * Opening the shelf widens that reservation to a fixed share of the screen, which squeezes the
 * workspace the same way a bottom shelf squeezes it by growing upward. A share rather than the
 * shelf's own content width because the panel and the notification section both fill whatever they
 * are given, so unbounded they would take the whole screen and leave the workspace nothing.
 */
private fun Modifier.dockAreaExtent(
    runsAlongASide: Boolean,
    isShelfOpen: Boolean,
    dock: DockModel,
    margins: GridInsets,
): Modifier =
    when {
        !runsAlongASide -> fillMaxWidth()
        isShelfOpen -> fillMaxHeight().fillMaxWidth(SIDE_DOCK_SHELF_WIDTH_FRACTION)
        else -> fillMaxHeight().width((dockCrossAxisDp(dock.iconSizeDp) + margins.start + margins.end).dp)
    }

/** True while the shelf's own expand gesture is attached, and so owns swipe-up on the dock. */
private fun DockInteractions.claimsSwipeUp(): Boolean =
    onShelfExpandedChange != null && shelfExpandAffordance == DockExpandAffordance.GESTURE

/**
 * The visible way into the expanded shelf, for a dock whose affordance is not the swipe.
 *
 * A single control that toggles, rather than separate expand and collapse ones: the expanded shelf
 * is drawn above this row, so the button keeps its position and only its meaning changes.
 */
@Composable
private fun DockShelfExpandButton(interactions: DockInteractions) {
    if (interactions.shelfExpandAffordance != DockExpandAffordance.BUTTON) return
    val onExpandedChange = interactions.onShelfExpandedChange ?: return
    val isExpanded = interactions.isShelfExpanded
    val label = if (isExpanded) "Collapse dock" else "Expand dock"
    TextButton(
        modifier = Modifier.testTag(HOME_DOCK_EXPAND_BUTTON_TEST_TAG).semantics { contentDescription = label },
        onClick = { onExpandedChange(!isExpanded) },
    ) {
        Text(text = if (isExpanded) "\u2304" else "\u2303")
    }
}

private fun DockAlignment.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        DockAlignment.START -> Alignment.Start
        DockAlignment.CENTER -> Alignment.CenterHorizontally
        DockAlignment.END -> Alignment.End
    }

internal fun GridInsets.nonNegative(): GridInsets =
    GridInsets(
        start = start.coerceAtLeast(0),
        top = top.coerceAtLeast(0),
        end = end.coerceAtLeast(0),
        bottom = bottom.coerceAtLeast(0),
    )

/** Settings expose margins per axis, so malformed or legacy asymmetric values stay centered. */
internal fun GridInsets.centered(): GridInsets {
    val nonNegative = nonNegative()
    val horizontal = maxOf(nonNegative.start, nonNegative.end)
    val vertical = maxOf(nonNegative.top, nonNegative.bottom)
    return GridInsets(start = horizontal, top = vertical, end = horizontal, bottom = vertical)
}

internal fun HomeLayout.shouldShowDock(): Boolean =
    dock.isEnabled &&
        dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = false,
            backgroundSizing = dock.backgroundSizing,
        )

/**
 * The band along the bottom that Cards mode leaves to the standard dock for physical input.
 *
 * A height, and only a height, because the dock Cards mode draws is the bottom-pinned one from
 * [StandardHomeDockOnlySurface] -- there, [DockModel.position] places the stage rail instead, so
 * the dock stays where it has always been however the rail is configured. Anything that starts
 * passing a position into that surface has to revisit this.
 */
internal fun HomeLayout.dockInteractionRegionHeightDp(): Int =
    if (!shouldShowDock()) {
        0
    } else {
        settings.grid.margin.centered().bottom + HOME_DOCK_TOP_SPACING_DP + dockCrossAxisDp(dock.iconSizeDp)
    }

/** How much of the screen an open side shelf takes, leaving the rest to the workspace. */
private const val SIDE_DOCK_SHELF_WIDTH_FRACTION = 0.5f

private const val HOME_DOCK_TOP_SPACING_DP = 10
internal const val HOME_DOCK_EXPAND_BUTTON_TEST_TAG = "home-dock-expand-button"
internal const val HOME_DOCK_TEST_TAG = "home-dock"
