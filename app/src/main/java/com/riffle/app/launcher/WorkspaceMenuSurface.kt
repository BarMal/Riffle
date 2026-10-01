package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceJumpEntry
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuAction
import com.riffle.core.domain.launcher.workspace.menu.WorkspaceMenuModel
import com.riffle.core.domain.launcher.workspace.menu.WorkspacePageKey

/**
 * The dock's workspace menu (#1351): a handle beside the dock while closed, and while open a scrim
 * and a panel on the dock's own edge listing switch workspace, jump to a page, Finder and Edit.
 *
 * It is the one place the menu is drawn and it takes no pointer input of its own beyond taps on rows,
 * the handle and the scrim: the dock pull is not involved (it stays the Home <-> Library switch), so
 * every action here is already an accessibility action. The panel is placed on [dockEdge] (left,
 * right, top or bottom, from the existing dock-edge logic), clear of the dock's [dockExtent] and of
 * the system bars and display cutouts, and fades in (instantly under [reducedMotion]).
 */
@Composable
internal fun WorkspaceMenuLayer(
    host: WorkspaceMenuHost,
    dockEdge: DockPosition,
    dockExtent: Dp,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val model = host.state.model
    Box(modifier = modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (host.state.isVisible && model != null) {
            BackHandler { host.onAction(WorkspaceMenuAction.Close) }
            WorkspaceMenuScrim(onDismiss = { host.onAction(WorkspaceMenuAction.Close) })
            WorkspaceMenuPanelFrame(dockEdge, dockExtent, reducedMotion) {
                WorkspaceMenuPanel(model = model, onAction = host.onAction)
            }
        } else {
            WorkspaceMenuHandle(
                dockEdge = dockEdge,
                dockExtent = dockExtent,
                onOpen = { host.onAction(WorkspaceMenuAction.Open) },
            )
        }
    }
}

/**
 * [WorkspaceMenuLayer] for the home destination: nothing for a null [host] (feature off), otherwise
 * clear of the dock's measured thickness and above the dock and both mode surfaces.
 */
@Composable
internal fun HomeWorkspaceMenu(
    host: WorkspaceMenuHost?,
    dockHost: HomeDockHostState,
    dockEdge: DockPosition,
    reducedMotion: Boolean,
) {
    if (host != null) {
        val dockExtentPx = dockHost.extentPx.intValue.coerceAtLeast(0)
        WorkspaceMenuLayer(
            host = host,
            dockEdge = dockEdge,
            dockExtent = with(LocalDensity.current) { dockExtentPx.toDp() },
            reducedMotion = reducedMotion,
            modifier = Modifier.zIndex(WORKSPACE_MENU_Z_INDEX),
        )
    }
}

@Composable
private fun WorkspaceMenuScrim(onDismiss: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = WORKSPACE_MENU_SCRIM_ALPHA))
                .clickable(onClickLabel = WORKSPACE_MENU_CLOSE_LABEL, role = Role.Button, onClick = onDismiss)
                .testTag(WORKSPACE_MENU_SCRIM_TEST_TAG),
    )
}

@Composable
private fun WorkspaceMenuPanelFrame(
    dockEdge: DockPosition,
    dockExtent: Dp,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    // Constructed already "entering": the panel fades in once, with nothing to animate on the way out.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier =
                Modifier
                    .align(WorkspaceMenuPlacement.panelAlignment(dockEdge))
                    .then(WorkspaceMenuPlacement.clearance(dockEdge, dockExtent + RiffleSpacing.s, RiffleSpacing.m)),
            enter = fadeIn(animationSpec = RiffleMotion.standard(reducedMotion)),
            exit = ExitTransition.None,
        ) {
            content()
        }
    }
}

@Composable
private fun WorkspaceMenuHandle(
    dockEdge: DockPosition,
    dockExtent: Dp,
    onOpen: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier =
                Modifier
                    .align(WorkspaceMenuPlacement.handleAlignment(dockEdge))
                    .then(WorkspaceMenuPlacement.clearance(dockEdge, dockExtent + RiffleSpacing.xs, RiffleSpacing.m))
                    .testTag(WORKSPACE_MENU_HANDLE_TEST_TAG),
            shape = RiffleShapes.full,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = RiffleElevation.level1,
            shadowElevation = RiffleElevation.level1,
        ) {
            Text(
                text = WORKSPACE_MENU_HANDLE_LABEL,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onOpen)
                        .heightIn(min = WORKSPACE_MENU_MIN_TARGET)
                        .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.s),
            )
        }
    }
}

@Composable
internal fun WorkspaceMenuPanel(
    model: WorkspaceMenuModel,
    onAction: (WorkspaceMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .widthIn(max = WORKSPACE_MENU_MAX_WIDTH)
                .semantics { paneTitle = WORKSPACE_MENU_TITLE }
                .testTag(WORKSPACE_MENU_PANEL_TEST_TAG),
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = RiffleElevation.level3,
        shadowElevation = RiffleElevation.level3,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = RiffleSpacing.s),
        ) {
            WorkspaceMenuSections(model, onAction)
        }
    }
}

@Composable
private fun WorkspaceMenuSections(
    model: WorkspaceMenuModel,
    onAction: (WorkspaceMenuAction) -> Unit,
) {
    val displayedName = model.switchEntries.firstOrNull { it.isDisplayed }?.name.orEmpty()
    MenuHeading(WORKSPACE_MENU_TITLE)
    model.fallback?.let { fallback ->
        val requested = model.switchEntries.firstOrNull { it.id == fallback.requested }?.name.orEmpty()
        Text(
            text = "Showing $displayedName because $requested can't be drawn on this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = RiffleSpacing.m).testTag(WORKSPACE_MENU_FALLBACK_TEST_TAG),
        )
    }
    if (model.hasSwitchChoice) {
        MenuHeading("Switch workspace")
        model.switchEntries.forEach { entry ->
            MenuRow(
                label = entry.name,
                selected = entry.isActive,
                onClick = { onAction(WorkspaceMenuAction.SwitchWorkspace(entry.id)) },
            )
        }
    } else {
        Text(
            text = displayedName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs),
        )
    }
    if (model.jumpEntries.isNotEmpty()) {
        MenuHeading("Jump to page")
        model.jumpEntries.forEach { entry ->
            MenuRow(label = entry.label(), onClick = { onAction(WorkspaceMenuAction.JumpToPage(entry.key)) })
        }
        if (model.omittedGroupCount > 0) {
            Text(
                text = "${model.omittedGroupCount} more groups are reached by paging.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = RiffleSpacing.m),
            )
        }
    }
    if (model.finder != null) {
        MenuRow(label = "Finder", onClick = { onAction(WorkspaceMenuAction.OpenFinder) })
    }
    MenuRow(label = "Edit workspace", onClick = { onAction(WorkspaceMenuAction.EditWorkspace) })
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.xs)
                .semantics { heading() },
    )
}

/** One tappable row: a radio-style row when [selected] is given, a plain button row otherwise. */
@Composable
private fun MenuRow(
    label: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
) {
    val tap =
        if (selected == null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = WORKSPACE_MENU_MIN_TARGET)
                .then(tap)
                .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected == true) {
            Text(text = "Current", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun WorkspaceJumpEntry.label(): String =
    when (key) {
        is WorkspacePageKey.Page -> "Page $pageNumber"
        is WorkspacePageKey.Group -> "${groupLabel ?: "Other"} (page $pageNumber)"
    }

/** Where the menu's pieces sit relative to the dock's edge (left and right are absolute, not RTL-mirrored). */
private object WorkspaceMenuPlacement {
    /** The panel sits on the dock's own edge, centred along it. */
    fun panelAlignment(dockEdge: DockPosition): Alignment =
        when (dockEdge) {
            DockPosition.BOTTOM -> Alignment.BottomCenter
            DockPosition.TOP -> Alignment.TopCenter
            DockPosition.LEFT -> AbsoluteAlignment.CenterLeft
            DockPosition.RIGHT -> AbsoluteAlignment.CenterRight
        }

    /** The handle sits at the far end of the dock's edge, beside the dock rather than over it. */
    fun handleAlignment(dockEdge: DockPosition): Alignment =
        when (dockEdge) {
            DockPosition.BOTTOM -> Alignment.BottomEnd
            DockPosition.TOP -> Alignment.TopEnd
            DockPosition.LEFT -> AbsoluteAlignment.BottomLeft
            DockPosition.RIGHT -> AbsoluteAlignment.BottomRight
        }

    /** [onEdge] of clearance on the dock's edge (the dock itself plus a gap), [elsewhere] on the other sides. */
    fun clearance(
        dockEdge: DockPosition,
        onEdge: Dp,
        elsewhere: Dp,
    ): Modifier =
        Modifier.absolutePadding(
            left = if (dockEdge == DockPosition.LEFT) onEdge else elsewhere,
            top = if (dockEdge == DockPosition.TOP) onEdge else elsewhere,
            right = if (dockEdge == DockPosition.RIGHT) onEdge else elsewhere,
            bottom = if (dockEdge == DockPosition.BOTTOM) onEdge else elsewhere,
        )
}

/** Above the dock while a pull lifts it (z 1) and above both mode surfaces. */
private const val WORKSPACE_MENU_Z_INDEX = 2f
private const val WORKSPACE_MENU_TITLE = "Workspace menu"
private const val WORKSPACE_MENU_HANDLE_LABEL = "Workspaces"
private const val WORKSPACE_MENU_CLOSE_LABEL = "Close workspace menu"
private const val WORKSPACE_MENU_SCRIM_ALPHA = 0.32f
private val WORKSPACE_MENU_MAX_WIDTH = 360.dp

/** The Material minimum touch target. */
private val WORKSPACE_MENU_MIN_TARGET = 48.dp

internal const val WORKSPACE_MENU_HANDLE_TEST_TAG = "workspace-menu-handle"
internal const val WORKSPACE_MENU_PANEL_TEST_TAG = "workspace-menu-panel"
internal const val WORKSPACE_MENU_SCRIM_TEST_TAG = "workspace-menu-scrim"
internal const val WORKSPACE_MENU_FALLBACK_TEST_TAG = "workspace-menu-fallback"
