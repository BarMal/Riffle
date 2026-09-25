package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.isHorizontalEdge

/**
 * What the one shared dock and the mode drawn beside it tell each other (#1205, Decision 2).
 *
 * The dock host is rendered once, outside the mode surface, so anything the mode's content and the
 * dock both need lives here rather than in either of them: the room the dock takes (which the
 * content lays out around), where it is in root coordinates (the space drag drop targets are
 * computed in), and whether something dragged in the content is over it. None of it is about a
 * particular mode; a mode writes what it knows and reads what it needs.
 */
@Stable
internal class HomeDockHostState {
    /** The dock's measured thickness across its edge, in px; [UNMEASURED_DOCK_EXTENT_PX] until laid out. */
    val extentPx = mutableIntStateOf(UNMEASURED_DOCK_EXTENT_PX)

    /** The dock's bounds in root coordinates -- the coordinate space the home grid's drop targets use. */
    val bounds: MutableState<Rect?> = mutableStateOf(null)

    /** Whether an item being dragged in the mode's content would land on the dock if released now. */
    val isDropTargetHighlighted: MutableState<Boolean> = mutableStateOf(false)

    /** Where a widget dragged out of the picker would land in the dock, if it is over it. */
    val widgetDropPreview: MutableState<WidgetPickerDockPlacementPreview?> = mutableStateOf(null)

    /** Whether a widget is being dragged out of the picker, which the dock yields its swipe-up to. */
    val isWidgetDragInProgress: MutableState<Boolean> = mutableStateOf(false)

    /** Whether the dock's shelf is open; the content's background tap closes it. */
    val isShelfExpanded: MutableState<Boolean> = mutableStateOf(false)

    /** A folder opened from the dock itself (folders on a home page are the content's own). */
    val openedFolderId: MutableState<LauncherItemId?> = mutableStateOf(null)

    fun dismissShelf() {
        isShelfExpanded.value = dockShelfExpandedStateAfterBackgroundTap(isExpanded = isShelfExpanded.value)
    }

    /** Clears what a content surface wrote, for when it leaves composition mid-gesture. */
    fun clearContentFeedback() {
        isDropTargetHighlighted.value = false
        widgetDropPreview.value = null
        isWidgetDragInProgress.value = false
    }
}

@Composable
internal fun rememberHomeDockHostState(): HomeDockHostState = remember { HomeDockHostState() }

/**
 * How the active mode interprets the shared dock's intents (Decision 2) -- the one interpreter
 * contract every mode supplies to [HomeDockHost]. The dock renders the one
 * [com.riffle.core.domain.launcher.home.DockModel] the same way in every mode and reports neutral
 * intents: a pinned app tapped (it always opens), a dynamic entry activated by key, a long-press menu
 * item chosen. Everything that differs by mode is derived from the mode at render time and supplied
 * here, so mode-specific behaviour (Cards' stage selector, for one; see CardsDockInterpreter.kt)
 * stays in that mode's own code and never in the dock. The default is the grid modes' reading.
 */
internal data class HomeDockInterpreter(
    /** What the dynamic side shows; null leaves it to notifications, which is what grid modes want. */
    val dynamicEntries: List<DockDynamicEntry>? = null,
    /** Receives the key of a dynamic entry whose intent is [DockDynamicEntryIntent.Delegate]. */
    val onDynamicEntryDelegated: (String) -> Unit = {},
    /** Items the mode adds to a pinned app's long-press menu; a tap on a pinned app always opens it. */
    val staticItemMenuExtras: DockItemMenuExtras = DockItemMenuExtras(),
    /**
     * Where the actions the dock sends go before reaching the shell -- a mode that reads some of
     * them in its own terms (Cards leaving "All" when a stage is selected) routes them here; null
     * sends them straight on.
     */
    val onAction: ((LauncherShellAction) -> Unit)? = null,
    /**
     * Whether the expanded shelf shows its notification card row. A mode whose own content already
     * *is* the notifications turns it off, leaving a panel-only shelf; the collapsed strip's dynamic
     * entries are [dynamicEntries] and stay either way.
     */
    val showExpandedNotificationShelf: Boolean = true,
)

/**
 * The one dock, pinned to its edge inside the safe drawing area -- the same place [StandardHome]'s
 * frame reserves for it and Cards pads its stage away from. Composed outside the mode surface, so
 * switching mode keeps this instance (and its position) rather than tearing one dock down and
 * building another.
 *
 * Callers compose it *before* the mode surface: overlays a mode draws (the widget picker, an open
 * folder, the Cards stage) then cover it, while a full-screen grid frame sits under it through
 * [HOME_CONTENT_Z_INDEX]. A folder opened from the dock is drawn here, above all of them.
 */
@Composable
@Suppress("LongParameterList")
internal fun HomeDockHost(
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    presentation: StandardHomePresentation,
    position: DockPosition,
    hostState: HomeDockHostState,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    interpreter: HomeDockInterpreter = HomeDockInterpreter(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    onExtentChanged: (Int) -> Unit = {},
) {
    val visibleLayout = layout.visibleTo(installedApps)
    val dockOnAction = interpreter.onAction ?: onAction
    val notificationShelfState =
        dockNotificationShelfState(
            dock = visibleLayout.dock,
            groups = presentation.notificationGroupsByApp,
            notificationAccessStatus = presentation.notificationAccessStatus,
            apps = presentation.installedApps,
        )
    val expandedShelfState =
        if (interpreter.showExpandedNotificationShelf) notificationShelfState else DockNotificationShelfState.Hidden
    val dockShelf =
        rememberDockShelfController(
            hasPanel = visibleLayout.dock.panel != null,
            notificationShelfState = expandedShelfState,
            isExpanded = hostState.isShelfExpanded,
        )
    if (!visibleLayout.shouldShowDock()) {
        // A hidden dock is never measured, so its last size would otherwise linger as a reservation.
        SideEffect { hostState.extentPx.intValue = 0 }
    }
    val actions =
        HomeWorkspaceActions(
            onFolderOpen = { folder -> hostState.openedFolderId.value = folder.id },
            onDragSessionChanged = {},
            haptics = haptics,
            onDockInteractionExtentChanged = { extentPx ->
                hostState.extentPx.intValue = extentPx
                onExtentChanged(extentPx)
            },
            onDockBoundsChanged = { bounds -> hostState.bounds.value = bounds },
            onBackgroundClick = dockShelf.dismiss,
            onAction = dockOnAction,
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(presentation.homeInsetPolicy.safeDrawingInsets()),
        contentAlignment = position.dockHostAlignment(LocalLayoutDirection.current),
    ) {
        StandardHomeDockArea(
            layout = visibleLayout,
            presentation = presentation,
            notificationShelfState = expandedShelfState,
            isDockShelfExpanded = dockShelf.isExpanded,
            onDockShelfExpandedChange = dockShelf.onExpandedChange,
            appIconLoader = appIconLoader,
            actions = actions,
            position = position,
            widgetPickerDockPreview = hostState.widgetDropPreview.value,
            isWidgetPickerInteractionActive =
                presentation.widgetPicker.isOpen || hostState.isWidgetDragInProgress.value,
            dynamicEntries = interpreter.dynamicEntries ?: notificationShelfState.dynamicEntries(),
            onDynamicEntryDelegated = interpreter.onDynamicEntryDelegated,
            staticItemMenuExtras = interpreter.staticItemMenuExtras,
            isDraggedItemOverDock = hostState.isDropTargetHighlighted.value,
        )
    }
    visibleLayout.openedFolder(hostState.openedFolderId.value)?.let { folder ->
        // Over whatever the mode drew after the dock: a folder opened from it is the thing in focus.
        Box(modifier = Modifier.zIndex(HOME_DOCK_FOLDER_Z_INDEX)) {
            FolderSurface(
                folder = folder,
                layout = visibleLayout,
                installedApps = installedApps,
                appIconLoader = appIconLoader,
                onDismiss = { hostState.openedFolderId.value = null },
                onAction = dockOnAction,
            )
        }
    }
}

/**
 * The room a mode's content leaves for the dock along [position]'s edge: the dock's measured
 * thickness once it has one, and until then the thickness its settings imply, so the first frame
 * already lays the content out close to where it settles.
 */
@Composable
internal fun HomeDockHostState.reservedExtent(
    layout: HomeLayout,
    position: DockPosition,
): Dp {
    val measuredPx = extentPx.intValue
    return if (measuredPx == UNMEASURED_DOCK_EXTENT_PX) {
        layout.dockInteractionRegionExtentDp(position).dp
    } else {
        with(LocalDensity.current) { measuredPx.toDp() }
    }
}

/**
 * The [Box] alignment that pins the dock to this edge. Every edge is honoured: a horizontal edge
 * centres along the top or bottom, a vertical one resolves to the physical left or right through
 * the same direction-aware mapping [StandardHome]'s frame orders its row by.
 */
private fun DockPosition.dockHostAlignment(direction: LayoutDirection): Alignment =
    if (isHorizontalEdge) {
        if (this == DockPosition.TOP) Alignment.TopCenter else Alignment.BottomCenter
    } else if (placedBeforeContent(direction)) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }

@Composable
private fun rememberDockShelfController(
    hasPanel: Boolean,
    notificationShelfState: DockNotificationShelfState,
    isExpanded: MutableState<Boolean>,
): DockShelfController {
    val hasContent =
        dockHasExpandedContent(
            hasPanel = hasPanel,
            notificationShelfState = notificationShelfState,
        )

    LaunchedEffect(hasContent) {
        isExpanded.value =
            dockShelfExpandedStateForContent(
                isExpanded = isExpanded.value,
                hasContent = hasContent,
            )
    }

    return DockShelfController(
        isExpanded = isExpanded.value,
        dismiss = {
            isExpanded.value = dockShelfExpandedStateAfterBackgroundTap(isExpanded = isExpanded.value)
        },
        onExpandedChange = { expanded -> isExpanded.value = expanded },
    )
}

private data class DockShelfController(
    val isExpanded: Boolean,
    val dismiss: () -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
)

private const val UNMEASURED_DOCK_EXTENT_PX = -1

/**
 * Where a full-screen mode frame sits relative to its siblings: below the dock, which is composed
 * before the mode surface but must keep its touches where the two overlap, and so below everything
 * else too -- the widget picker and open folders, composed after the dock, still cover it.
 */
internal const val HOME_CONTENT_Z_INDEX = -1f

private const val HOME_DOCK_FOLDER_Z_INDEX = 1f
