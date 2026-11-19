@file:Suppress("ComplexCondition", "CyclomaticComplexMethod", "LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.glance.rememberRiffleGlanceProviders
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HandleAxis
import com.riffle.core.domain.launcher.home.HandleStrip
import com.riffle.core.domain.launcher.home.HomeDragPages
import com.riffle.core.domain.launcher.home.HomeDragPlan
import com.riffle.core.domain.launcher.home.HomeDragPush
import com.riffle.core.domain.launcher.home.HomeDragTuning
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeHandle
import com.riffle.core.domain.launcher.home.HomeHandleLayout
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.handleAxis
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings
import com.riffle.core.domain.launcher.settings.homeSystemBars
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun StandardHome(
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    interactions: StandardHomeInteractions,
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetPreviewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
    deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
    /**
     * The shared dock host this frame lays out around, when the dock is drawn by the caller outside
     * the mode surface (#1205). Null draws the same host here, for callers that show this alone.
     */
    dockHost: HomeDockHostState? = null,
    /**
     * The edge the dock sits on for this surface, when the caller resolves it (Library keeps its own
     * edge, see ModeDockEdges.kt); null resolves the shared dock's configured or template edge here.
     */
    dockEdge: DockPosition? = null,
    /** Applied to the grid frame; the dock pull slides the surface through it. */
    surfaceModifier: Modifier = Modifier,
    onAction: (LauncherShellAction) -> Unit,
) {
    val visibleLayout = layout.visibleTo(installedApps)
    val dockHostState = dockHost ?: rememberHomeDockHostState()
    // Single source of truth for where the dock sits: the user's configured edge if any, else the
    // device class's template default (bottom on phones, the leading rail on wide postures -- see
    // #1159). The widget-picker drop previews below and the rendered dock must agree on it.
    val dockPosition =
        dockEdge ?: resolveDockPosition(visibleLayout.dock.position, deviceClass.templateDockPosition)
    val openedFolderId = remember { mutableStateOf<LauncherItemId?>(null) }
    val renameOpenedFolder = remember { mutableStateOf(false) }
    // A drop that makes a folder opens it with its name in edit mode: the folders before the drop are remembered, and
    // the first folder beyond them is the new one.
    val foldersBeforeDrop = remember { mutableStateOf<Set<LauncherItemId>?>(null) }
    val folderIds = layout.pages.flatMap { it.items }.filterIsInstance<FolderItem>().map { it.id }.toSet()
    LaunchedEffect(folderIds) {
        val before = foldersBeforeDrop.value ?: return@LaunchedEffect
        val created = (folderIds - before).firstOrNull() ?: return@LaunchedEffect
        foldersBeforeDrop.value = null
        renameOpenedFolder.value = true
        openedFolderId.value = created
    }
    val homeDragSession = remember { mutableStateOf<HomeDragSession?>(null) }
    // What composition sees of the drag: everything but where the finger is. The finger moves every frame; the lifted
    // icon and the edge hold read it in the layout phase and from the latest session, so composition (and every cell
    // of every page) only changes when the drag does: a new landing cell, a new push, a page turn.
    // A surface above may hand in the motion (a test reads the settle through it); otherwise this one's own.
    val itemMotion = LocalHomeItemMotion.current ?: remember { HomeItemMotion() }
    val composedDragSession =
        remember {
            derivedStateOf(structuralEqualityPolicy()) { homeDragSession.value?.withoutPointer() }
        }
    // The page a drag offers past the last one: asked for (the pages before it noted), then found in the layout, kept
    // when the drag ends on it and discarded otherwise.
    val pagesBeforeCandidate = remember { mutableStateOf<Set<LauncherPageId>?>(null) }
    val candidatePageId = remember { mutableStateOf<LauncherPageId?>(null) }
    val discardCandidate = remember { mutableStateOf<LauncherPageId?>(null) }
    LaunchedEffect(discardCandidate.value) {
        val candidate = discardCandidate.value ?: return@LaunchedEffect
        discardCandidate.value = null
        onAction(LauncherShellAction.DiscardEmptyHomePage(candidate))
    }
    LaunchedEffect(layout.pages) {
        val before = pagesBeforeCandidate.value ?: return@LaunchedEffect
        layout.pages.firstOrNull { it.id !in before }?.let { added ->
            candidatePageId.value = added.id
            pagesBeforeCandidate.value = null
        }
    }
    // Read by the dock host as well as here, so they live in its state rather than this frame's.
    val widgetPickerDragInProgress = dockHostState.isWidgetDragInProgress
    val widgetPickerDragPreview = remember { mutableStateOf<WidgetPickerDragPlacementPreview?>(null) }
    val widgetPickerDockPreview = dockHostState.widgetDropPreview
    val latestWidgetPickerDrag = remember { mutableStateOf<WidgetPickerDragSnapshot?>(null) }
    val accessibleWidgetPlacement = remember { mutableStateOf<WidgetPickerAccessiblePlacement?>(null) }
    val activeWidgetPickerEdgeHoverSide = remember { mutableStateOf<WidgetPickerEdgeHoverSide?>(null) }
    val widgetPickerDragWorkspaceBounds = remember { mutableStateOf<Rect?>(null) }
    val workspaceGridBounds = remember { mutableStateOf<Rect?>(null) }
    val dockBounds = dockHostState.bounds
    val density = LocalDensity.current.density
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val finishWidgetPickerDrag: (Boolean) -> Unit = { restorePicker ->
        val wasInProgress = widgetPickerDragInProgress.value
        widgetPickerDragInProgress.value = false
        widgetPickerDragPreview.value = null
        widgetPickerDockPreview.value = null
        latestWidgetPickerDrag.value = null
        activeWidgetPickerEdgeHoverSide.value = null
        widgetPickerDragWorkspaceBounds.value = null
        if (restorePicker && wasInProgress) {
            onAction(LauncherShellAction.OpenWidgetPicker)
        }
    }
    val cancelAccessibleWidgetPlacement = {
        val placement = accessibleWidgetPlacement.value
        accessibleWidgetPlacement.value = null
        placement
            ?.let { value ->
                accessibleWidgetPlacementCancellationActionFor(
                    placement = value,
                    selectedPageId = visibleLayout.selectedPageId,
                )
            }?.let(onAction)
        Unit
    }
    BackHandler(enabled = widgetPickerDragInProgress.value || accessibleWidgetPlacement.value != null) {
        if (widgetPickerDragInProgress.value) {
            finishWidgetPickerDrag(true)
        } else {
            cancelAccessibleWidgetPlacement()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFinishWidgetPickerDrag = rememberUpdatedState(finishWidgetPickerDrag)
    val latestCancelAccessibleWidgetPlacement = rememberUpdatedState(cancelAccessibleWidgetPlacement)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    latestFinishWidgetPickerDrag.value(true)
                    latestCancelAccessibleWidgetPlacement.value()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // A mode switch mid-drag takes this frame away; the dock it was talking to stays.
    DisposableEffect(dockHostState) {
        onDispose { dockHostState.clearContentFeedback() }
    }
    LaunchedEffect(workspaceGridBounds.value) {
        val startingBounds = widgetPickerDragWorkspaceBounds.value
        if (
            widgetPickerDragInProgress.value &&
            startingBounds != null &&
            workspaceGridBounds.value != startingBounds
        ) {
            finishWidgetPickerDrag(true)
        }
    }
    LaunchedEffect(
        latestWidgetPickerDrag.value,
        visibleLayout.selectedPage,
        workspaceGridBounds.value,
        dockBounds.value,
        density,
    ) {
        widgetPickerDragPreview.value =
            latestWidgetPickerDrag.value?.let { snapshot ->
                widgetPickerDragPlacementPreviewFor(
                    snapshot = snapshot,
                    page = visibleLayout.selectedPage,
                    workspaceBounds = workspaceGridBounds.value,
                    dockBounds = dockBounds.value,
                    density = density,
                )
            }
    }
    LaunchedEffect(activeWidgetPickerEdgeHoverSide.value, visibleLayout.selectedPageId) {
        val side = activeWidgetPickerEdgeHoverSide.value ?: return@LaunchedEffect
        delay(WIDGET_PICKER_EDGE_HOVER_DELAY_MILLIS)
        val targetPageId =
            widgetPickerEdgeHoverPageId(
                side = side,
                pages = visibleLayout.pages,
                selectedPageId = visibleLayout.selectedPageId,
                isRtl = isRtl,
            )
        if (widgetPickerDragInProgress.value && activeWidgetPickerEdgeHoverSide.value == side && targetPageId != null) {
            onAction(LauncherShellAction.SelectHomePage(targetPageId))
        } else if (targetPageId == null) {
            activeWidgetPickerEdgeHoverSide.value = null
        }
    }
    // A Dock item dragged out onto Home lands on the page and cell under the finger.
    val latestHomeDrop = rememberUpdatedState(Pair(visibleLayout.selectedPage, workspaceGridBounds.value))
    DisposableEffect(dockHostState) {
        dockHostState.homeDropAt = { point ->
            homeDropTargetAt(latestHomeDrop.value.first, latestHomeDrop.value.second, point)
        }
        onDispose { dockHostState.homeDropAt = null }
    }
    val freshActions =
        HomeWorkspaceActions(
            onFolderOpen = { folder ->
                renameOpenedFolder.value = false
                openedFolderId.value = folder.id
            },
            onFolderRename = { folder ->
                renameOpenedFolder.value = true
                openedFolderId.value = folder.id
            },
            onDragNewPage = {
                if (candidatePageId.value == null && pagesBeforeCandidate.value == null) {
                    pagesBeforeCandidate.value = layout.pages.map { it.id }.toSet()
                    onAction(LauncherShellAction.AddHomePage)
                }
            },
            onDragSessionChanged = { session ->
                if (session == null) {
                    homeDragSession.value?.let { ended -> itemMotion.letGo(ended.item.id, ended.pointerInRoot) }
                    // The drop's own action follows this at once and keeps the page it lands on; whatever is still
                    // pending after it (a cancel, a drop elsewhere) is discarded.
                    discardCandidate.value = candidatePageId.value
                    candidatePageId.value = null
                    pagesBeforeCandidate.value = null
                }
                val planned = session?.planned(visibleLayout, workspaceGridBounds.value, presentation.dragTuning)
                if (planned?.plan?.pushed?.keys != homeDragSession.value?.plan?.pushed?.keys &&
                    planned?.plan?.pushed?.isNotEmpty() == true
                ) {
                    interactions.haptics.perform(LauncherHapticEvent.DETENT)
                }
                // Over the Dock, the slot the finger hovers is where the item is pinned.
                val hovered =
                    planned?.takeIf { it.isOverDock }?.let { over ->
                        val slots = layout.dock.items.mapNotNull { item -> dockHostState.pinnedSlotBounds[item.id] }
                        if (slots.size == layout.dock.items.size && slots.isNotEmpty()) {
                            dockInsertionIndexAmong(over.pointerInRoot, dockBounds.value, slots, isRtl, dockPosition)
                        } else {
                            val count = layout.dock.items.size
                            dockInsertionIndex(over.pointerInRoot, dockBounds.value, count, isRtl, dockPosition)
                        }
                    }
                homeDragSession.value = planned?.copy(dockIndex = hovered)
                dockHostState.isDropTargetHighlighted.value = planned?.isOverDock == true
                dockHostState.isHomeDragInProgress.value = planned != null
            },
            currentDragSession = { homeDragSession.value },
            haptics = interactions.haptics,
            onDockInteractionExtentChanged = interactions.onDockInteractionExtentChanged,
            onBottomControlsHeightChanged = interactions.onBottomControlsHeightChanged,
            onWorkspaceGridBoundsChanged = { pageId, bounds ->
                if (pageId == visibleLayout.selectedPageId) {
                    workspaceGridBounds.value = bounds
                }
            },
            onBackgroundClick = dockHostState::dismissShelf,
            onAction = { action ->
                if (action is LauncherShellAction.DropHomeItem &&
                    discardCandidate.value?.let { HomeDragPages.keepsCandidate(it, action.targetPageId) } == true
                ) {
                    discardCandidate.value = null
                }
                if (action is LauncherShellAction.MoveHomeShortcutToCell ||
                    (action is LauncherShellAction.DropHomeItem && action.dropOnItemId != null)
                ) {
                    foldersBeforeDrop.value = folderIds
                }
                onAction(action)
            },
        )
    // One instance for the life of the surface, which hands every call to the latest: the cells compare what they are
    // given, and a fresh bundle of lambdas at every recomposition would make every cell recompose with it.
    val actions = rememberStableWorkspaceActions(freshActions)

    CompositionLocalProvider(LocalHomeItemMotion provides itemMotion) {
        StandardHomeColumn(
            state =
                StandardHomeContentState(
                    layout = layout,
                    visibleLayout = visibleLayout,
                    dockPosition = dockPosition,
                    dragSession = composedDragSession.value,
                    widgetPickerDragPreview = widgetPickerDragPreview.value,
                    presentation = presentation,
                    dockReservation = dockHostState.reservedExtent(visibleLayout, dockPosition),
                ),
            appIconLoader = appIconLoader,
            actions = actions,
            modifier = surfaceModifier,
        )
    }
    // The lifted icon above the pager (and so above a page turn), read in the layout phase.
    if (composedDragSession.value != null) {
        HomeLiftedItem(
            session = { homeDragSession.value },
            presentation = homeGridPresentationFor(layout, presentation, actions),
            appIconLoader = appIconLoader,
        )
    }
    if (dockHost == null) {
        HomeDockHost(
            layout = layout,
            installedApps = installedApps,
            presentation = presentation,
            position = dockPosition,
            hostState = dockHostState,
            appIconLoader = appIconLoader,
            onAction = onAction,
            haptics = interactions.haptics,
            onExtentChanged = interactions.onDockInteractionExtentChanged,
        )
    }
    if (presentation.widgetPicker.isOpen || widgetPickerDragInProgress.value) {
        val riffleGlance = rememberRiffleGlanceProviders()
        // Riffle's own widgets belong to the personal profile, which is always usable.
        val riffleGlanceProfileVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE)
        WidgetPickerSurface(
            providers = riffleGlance + presentation.widgetPicker.providers,
            profileContentVisibility =
                riffleGlanceProfileVisibility + presentation.widgetPicker.profileContentVisibility,
            catalogStatus = presentation.widgetPicker.catalogStatus,
            previewImageLoader = widgetPreviewImageLoader,
            accessiblePlacement = accessibleWidgetPlacement.value,
            hasDockPanel = visibleLayout.dock.panel != null,
            guidedPlacementTarget =
                WidgetAddTarget.DOCK_PANEL.takeIf { presentation.widgetPicker.isTargetingDockPanel },
            isDragHandoffActive = widgetPickerDragInProgress.value,
            onWidgetDragStarted = {
                cancelAccessibleWidgetPlacement()
                widgetPickerDragInProgress.value = true
                widgetPickerDragPreview.value = null
                widgetPickerDockPreview.value = null
                latestWidgetPickerDrag.value = null
                activeWidgetPickerEdgeHoverSide.value = null
                widgetPickerDragWorkspaceBounds.value = workspaceGridBounds.value
                onAction(LauncherShellAction.CloseWidgetPicker)
            },
            onWidgetDragMoved = { provider, position, rootSize ->
                val bounds = workspaceGridBounds.value
                val snapshot =
                    WidgetPickerDragSnapshot(
                        provider = provider,
                        position = position,
                        rootSize = rootSize,
                    )
                latestWidgetPickerDrag.value = snapshot
                activeWidgetPickerEdgeHoverSide.value =
                    bounds?.let { workspaceBounds ->
                        widgetPickerEdgeHoverSide(
                            position = position,
                            workspaceBounds = workspaceBounds,
                            edgeZonePx = WIDGET_PICKER_EDGE_HOVER_ZONE_DP * density,
                        )
                    }
                widgetPickerDragPreview.value =
                    widgetPickerDragPlacementPreviewFor(
                        snapshot = snapshot,
                        page = visibleLayout.selectedPage,
                        workspaceBounds = bounds,
                        dockBounds = dockBounds.value,
                        density = density,
                    )
                widgetPickerDockPreview.value =
                    widgetPickerDockPlacementPreviewFor(
                        snapshot = snapshot,
                        dock = visibleLayout.dock,
                        dockBounds = dockBounds.value,
                        isRtl = isRtl,
                        position = dockPosition,
                    )
            },
            onWidgetDragCancelled = {
                finishWidgetPickerDrag(true)
            },
            onAccessiblePlacementRequested = { provider, target ->
                accessibleWidgetPlacement.value =
                    accessibleWidgetPlacementFor(
                        provider = provider,
                        target = target,
                        pages = visibleLayout.pages,
                        selectedPageId = visibleLayout.selectedPageId,
                        initialPageId =
                            accessibleWidgetPlacement.value?.initialPageId
                                ?: visibleLayout.selectedPageId,
                        dockItemCount = visibleLayout.dock.items.size,
                        panel = visibleLayout.dock.panel,
                        availableWidthDp = workspaceGridBounds.value?.width?.div(density)?.roundToInt() ?: 0,
                        availableHeightDp = workspaceGridBounds.value?.height?.div(density)?.roundToInt() ?: 0,
                    )
            },
            onAccessiblePlacementSelected = { placement ->
                accessibleWidgetPlacement.value = placement
                placement.selectedCandidate?.pageId?.let { pageId ->
                    if (pageId != visibleLayout.selectedPageId) {
                        onAction(LauncherShellAction.SelectHomePage(pageId))
                    }
                }
            },
            onAccessiblePlacementConfirmed = {
                accessibleWidgetPlacement.value?.takeIf { placement -> placement.isValid }?.let { placement ->
                    accessibleWidgetAddActionFor(placement)?.let { action ->
                        onAction(action)
                        accessibleWidgetPlacement.value = null
                        onAction(LauncherShellAction.CloseWidgetPicker)
                    }
                }
            },
            onAccessiblePlacementCancelled = cancelAccessibleWidgetPlacement,
            onCloseRequested = {
                cancelAccessibleWidgetPlacement()
                onAction(LauncherShellAction.CloseWidgetPicker)
            },
            onRetryRequested = { onAction(LauncherShellAction.OpenWidgetPicker) },
            onWidgetDropped = { provider, position, rootSize ->
                val selectedPage = visibleLayout.selectedPage
                val bounds = workspaceGridBounds.value
                val snapshot =
                    WidgetPickerDragSnapshot(
                        provider = provider,
                        position = position,
                        rootSize = rootSize,
                    )
                val target =
                    bounds?.let {
                        widgetPickerDropTarget(position, workspaceBounds = it, dockBounds = dockBounds.value)
                    }
                val preview =
                    widgetPickerDragPlacementPreviewFor(
                        snapshot = snapshot,
                        page = selectedPage,
                        workspaceBounds = bounds,
                        dockBounds = dockBounds.value,
                        density = density,
                    )
                val dockPreview =
                    widgetPickerDockPlacementPreviewFor(
                        snapshot = snapshot,
                        dock = visibleLayout.dock,
                        dockBounds = dockBounds.value,
                        isRtl = isRtl,
                        position = dockPosition,
                    )
                val isValidDrop =
                    widgetPickerDropIsValid(
                        target = target,
                        homePreview = preview,
                        dockPreview = dockPreview,
                    )
                if (isValidDrop && target != null) {
                    onAction(
                        LauncherShellAction.RequestAddWidget(
                            provider = provider.identity,
                            label = provider.label,
                            dimensions = provider.dimensions,
                            target = target,
                            targetPageId = selectedPage.id.takeIf { target == WidgetAddTarget.HOME },
                            targetCell = preview?.cell.takeIf { target == WidgetAddTarget.HOME },
                            dockIndex = dockPreview?.dockIndex.takeIf { target == WidgetAddTarget.DOCK },
                        ),
                    )
                }
                finishWidgetPickerDrag(!isValidDrop)
            },
            onAction = onAction,
        )
    }
    visibleLayout.openedFolder(openedFolderId.value)?.let { folder ->
        FolderSurface(
            folder = folder,
            layout = visibleLayout,
            installedApps = installedApps,
            appIconLoader = appIconLoader,
            onDismiss = { openedFolderId.value = null },
            onAction = onAction,
            editTitleOnOpen = renameOpenedFolder.value,
        )
    }
}

@Suppress("MaxLineLength")
internal fun accessibleWidgetAddActionFor(
    placement: WidgetPickerAccessiblePlacement,
): LauncherShellAction.RequestAddWidget? {
    val candidate = placement.selectedCandidate ?: return null
    return LauncherShellAction.RequestAddWidget(
        provider = placement.provider.identity,
        label = placement.provider.label,
        dimensions = placement.provider.dimensions,
        supportsHorizontalResize = placement.provider.supportsHorizontalResize,
        supportsVerticalResize = placement.provider.supportsVerticalResize,
        target = placement.target,
        targetPageId = candidate.pageId,
        targetCell = candidate.cell,
        dockIndex = candidate.dockIndex,
    )
}

internal fun accessibleWidgetPlacementCancellationActionFor(
    placement: WidgetPickerAccessiblePlacement,
    selectedPageId: LauncherPageId,
): LauncherShellAction.SelectHomePage? =
    placement.initialPageId
        .takeIf { initialPageId -> initialPageId != selectedPageId }
        ?.let(LauncherShellAction::SelectHomePage)

@Suppress("LongParameterList")
internal fun accessibleWidgetPlacementFor(
    provider: InstalledWidgetProvider,
    target: WidgetAddTarget,
    pages: List<LauncherPage>,
    selectedPageId: LauncherPageId,
    initialPageId: LauncherPageId = selectedPageId,
    dockItemCount: Int = 0,
    panel: LauncherPage? = null,
    availableWidthDp: Int,
    availableHeightDp: Int,
): WidgetPickerAccessiblePlacement {
    if (target == WidgetAddTarget.DOCK) {
        return WidgetPickerAccessiblePlacement(
            provider = provider,
            target = target,
            initialPageId = initialPageId,
            candidates =
                (0..dockItemCount).map { index ->
                    WidgetPickerPlacementCandidate(dockIndex = index)
                },
        )
    }

    // The panel is a page with a grid, so free cells are found on it exactly as on a home page. It
    // is the only page in play when it is the target, and its candidates carry no page ID -- it is
    // not one of the layout's pages, and nothing downstream should try to navigate to it.
    val searchedPages =
        when (target) {
            WidgetAddTarget.DOCK_PANEL -> listOfNotNull(panel)
            else -> pages.sortedBy { page -> if (page.id == selectedPageId) 0 else 1 }
        }
    val candidates =
        searchedPages.flatMap { page ->
            page.freeCellCandidates(
                provider = provider,
                pageId = page.id.takeIf { target != WidgetAddTarget.DOCK_PANEL },
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            )
        }
    return WidgetPickerAccessiblePlacement(
        provider = provider,
        target = target,
        initialPageId = initialPageId,
        candidates = candidates,
    )
}

private fun LauncherPage.freeCellCandidates(
    provider: InstalledWidgetProvider,
    pageId: LauncherPageId?,
    availableWidthDp: Int,
    availableHeightDp: Int,
): List<WidgetPickerPlacementCandidate> =
    (0 until grid.rows.coerceAtLeast(0)).flatMap { row ->
        (0 until grid.columns.coerceAtLeast(0)).mapNotNull { column ->
            widgetPickerDragPlacementPreviewFor(
                page = this,
                provider = provider,
                cell = GridCell(column = column, row = row),
                availableWidthDp = availableWidthDp,
                availableHeightDp = availableHeightDp,
            ).takeIf { preview -> preview.isValid }?.let { preview ->
                WidgetPickerPlacementCandidate(
                    pageId = pageId,
                    cell = preview.cell,
                    span = preview.span,
                )
            }
        }
    }

@Suppress("LongMethod")
@Composable
private fun StandardHomeColumn(
    state: StandardHomeContentState,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    modifier: Modifier = Modifier,
) {
    val pagerState =
        rememberImmediateHomePagerState(
            layout = state.visibleLayout,
            reducedMotion = state.presentation.reducedMotion,
            actions = actions,
        )
    // Keyed on a request id rather than the target page itself, so a new page crossed mid-drag
    // explicitly supersedes and cancels whatever snap the previous one was still mid-flight on --
    // the same superseding-coroutine pattern PageIndicator's own live-offset tracking uses, rather
    // than leaning on PagerState's own scroll-mutex preemption as the only thing keeping fast,
    // overlapping scrub updates in order. Keying on the target page directly would silently skip a
    // request whenever it repeats whatever page a previous, unrelated request already left recorded
    // (a cancelled drag reverting to page 0, then a later one's first crossed page also being 0);
    // the id guarantees every request is treated as new regardless of what page it names.
    val scrubRequestId = remember { mutableStateOf(0) }
    val scrubTargetPageIndex = remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(scrubRequestId.value) {
        scrubTargetPageIndex.value?.let { index -> pagerState.snapToPage(index) }
    }
    val onIndicatorScrub: (Int) -> Unit = { index ->
        scrubTargetPageIndex.value = index
        scrubRequestId.value += 1
    }
    val margins = state.visibleLayout.settings.grid.margin.centered()
    val dockEdge = state.dockPosition
    // The dock itself is drawn by HomeDockHost, over this frame and outside the mode surface
    // (#1205); the frame only keeps its room, so the workspace lays out beside it rather than under.
    val dockArea: @Composable () -> Unit = {
        if (dockEdge.isSideEdge) {
            Spacer(modifier = Modifier.fillMaxHeight().width(state.dockReservation))
        } else {
            Spacer(modifier = Modifier.height(state.dockReservation))
        }
    }

    val layoutSettings = state.presentation.layout
    // Beside a side Dock the handle is always the column along it; above a bottom Dock it follows the pages.
    val handleAxis = HomeHandleLayout.axisFor(dockEdge, layoutSettings.pageScroll)
    val browsing = state.visibleLayout.editMode == HomeEditMode.Browsing
    // A column of dots beside a side Dock stands in a column of its own just in from the Dock, in front of its middle;
    // a row stays in the row below the pages (above a bottom Dock), inside the bottom controls. While pages are being
    // edited there is no handle.
    val handleInStrip = browsing && HomeHandleLayout.stripFor(dockEdge) != HandleStrip.BOTTOM_ROW
    // The column stands in the pages' margin on the Dock's side first, so the grid gives up only what that margin
    // could not hold (the way the row above a bottom Dock takes its height), and lays out in the rest.
    val handleReservedDp =
        if (handleInStrip) {
            val pageCount = state.visibleLayout.pages.size
            HomeHandleLayout.reservedDp(
                dockEdge,
                handleAxis,
                pageCount,
                layoutSettings.showsDots && HomeHandle.showsDots(pageCount),
            )
        } else {
            0
        }
    val pagerStartMargin =
        if (dockEdge == DockPosition.LEFT) {
            HomeHandleLayout.remainingMarginDp(margins.start.coerceAtLeast(0), handleReservedDp)
        } else {
            margins.start.coerceAtLeast(0)
        }
    val pagerEndMargin =
        if (dockEdge == DockPosition.RIGHT) {
            HomeHandleLayout.remainingMarginDp(margins.end.coerceAtLeast(0), handleReservedDp)
        } else {
            margins.end.coerceAtLeast(0)
        }
    val onOpenPage: (Int) -> Unit = { pageIndex ->
        state.visibleLayout.pages.getOrNull(pageIndex)?.let { page ->
            actions.onAction(LauncherShellAction.SelectHomePage(page.id))
        }
    }

    // Home's menu: the background opens it where the finger is, and so does a long-press on the handle.
    val menuState = remember { PressMenuState() }
    val onHandleLongPress: (Offset) -> Unit = { point ->
        actions.haptics.longPress()
        menuState.show(point)
    }
    StandardHomeFrame(
        dockEdge = dockEdge,
        dockArea = dockArea,
        handleStrip =
            if (handleInStrip) {
                {
                    HomeHandleStrip(
                        dockEdge = dockEdge,
                        axis = handleAxis,
                        pageCount = state.visibleLayout.pages.size,
                        selectedPageIndex = pagerState.visualSelectedPageIndex,
                        layout = layoutSettings,
                        reducedMotion = state.presentation.reducedMotion,
                        onPageSelected = onOpenPage,
                        onIndicatorScrub = onIndicatorScrub,
                        onHandleLongPress = onHandleLongPress,
                        actions = actions,
                        dock = state.visibleLayout.dock,
                        hidden = state.presentation.handleHidden,
                    )
                }
            } else {
                null
            },
        modifier =
            Modifier
                // Below the dock HomeDockHost draws over this frame, so the dock keeps its touches.
                .zIndex(HOME_CONTENT_Z_INDEX)
                .fillMaxSize()
                .then(modifier)
                .homeGestureInput(
                    enabled = state.visibleLayout.editMode == HomeEditMode.Browsing,
                    settings = state.presentation.homeGestures,
                    onAction = actions.onAction,
                ),
        insets = state.presentation.homeInsetPolicy.contentInsets(),
        // The one empty-space handler of Home, under everything and as big as the window: the margins, the room the
        // Dock and the handle reserve, the system bars and an empty page are all Home's background.
        background = {
            HomeBackgroundContextMenu(
                haptics = actions.haptics,
                onAction = actions.onAction,
                onClick = actions.onBackgroundClick,
                state = menuState,
            )
        },
    ) {
        if (dockEdge == DockPosition.TOP) dockArea()
        ImmediateWorkspacePager(
            layout = state.visibleLayout,
            pagerState = pagerState,
            gridState =
                HomeGridState(
                    isEditing = state.visibleLayout.editMode is HomeEditMode.EditingPage,
                    pageCount = state.visibleLayout.pages.size,
                    selectedPageIndex = state.visibleLayout.selectedPageIndex,
                    dragSession = state.dragSession,
                    widgetPickerDragPreview = state.widgetPickerDragPreview,
                ),
            presentation = state.homeGridPresentation(actions),
            appIconLoader = appIconLoader,
            actions = actions,
            vertical = layoutSettings.pageScroll == PageScrollDirection.VERTICAL,
            activeDragSession = state.dragSession,
            onDragNewPage = actions.onDragNewPage,
            onDragPageTargetChanged = { pageId ->
                actions.currentDragSession()?.let { session ->
                    if (session.targetPageId != pageId) {
                        actions.onDragSessionChanged(session.copy(targetPageId = pageId))
                        actions.onAction(LauncherShellAction.SelectHomePage(pageId))
                    }
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = pagerStartMargin.dp,
                        top = margins.top.coerceAtLeast(0).dp,
                        end = pagerEndMargin.dp,
                        bottom = margins.bottom.coerceAtLeast(0).dp,
                    ),
        )
        Spacer(modifier = Modifier.height(state.visibleLayout.dock.homeControlsSpacingDp.dp))
        HomeBottomControls(
            layout = state.visibleLayout,
            selectedPageIndex = pagerState.visualSelectedPageIndex,
            reducedMotion = state.presentation.reducedMotion,
            onIndicatorScrub = onIndicatorScrub,
            handle = if (handleInStrip) null else layoutSettings,
            onHandleLongPress = onHandleLongPress,
            handleHidden = state.presentation.handleHidden,
            handleAxis = handleAxis,
            appIconLoader = appIconLoader,
            widgetViewFactory = state.presentation.widgetViewFactory,
            actions = actions,
            modifier =
                Modifier.onSizeChanged { size -> actions.onBottomControlsHeightChanged(size.height) },
        )
        if (!dockEdge.isSideEdge && dockEdge != DockPosition.TOP) dockArea()
    }
}

/**
 * The home screen's frame: the workspace, and the room for the dock on whichever edge it has been
 * given. The dock itself is drawn by [HomeDockHost] over this frame; [dockArea] only reserves it.
 *
 * A dock on a side sits beside the whole workspace column, reserving its width -- which is the
 * width the grid has already given up a column for. Otherwise the workspace column reserves the
 * dock's height itself, at the bottom (or the top), so it keeps its place beyond the page controls.
 */
@Composable
private fun StandardHomeFrame(
    dockEdge: DockPosition,
    dockArea: @Composable () -> Unit,
    handleStrip: (@Composable () -> Unit)?,
    modifier: Modifier,
    /** The window the frame stands in, less the system bars: the frame's content lays out inside it. */
    insets: WindowInsets,
    /** Home's empty-space handler, drawn first and over the whole frame, including the room under [insets]. */
    background: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        background()
        val inner = Modifier.fillMaxSize().windowInsetsPadding(insets)
        if (!dockEdge.isSideEdge) {
            Column(
                modifier = inner,
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        } else {
            // The edges are physical, but a Row lays children start-to-end and mirrors in RTL. Placing the
            // dock by physical side keeps a left dock on the left either way.
            val dockLeadsRow = dockEdge.placedBeforeContent(LocalLayoutDirection.current)
            Row(modifier = inner) {
                if (dockLeadsRow) {
                    dockArea()
                    handleStrip?.invoke()
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
                if (!dockLeadsRow) {
                    handleStrip?.invoke()
                    dockArea()
                }
            }
        }
    }
}

/**
 * Whether this edge puts the dock beside the workspace rather than above or below it.
 *
 * The top edge is not one. The shared dock no longer offers it (see sharedDockPositions), but a
 * layout that still holds it gets its room reserved at the top, where [HomeDockHost] draws it.
 */
private val DockPosition.isSideEdge: Boolean
    get() = this == DockPosition.LEFT || this == DockPosition.RIGHT

@Composable
private fun HomeBottomControls(
    layout: HomeLayout,
    selectedPageIndex: Int,
    reducedMotion: Boolean,
    onIndicatorScrub: (Int) -> Unit,
    /** The layout the handle follows, or null when the handle is not in this row (it stands beside a side Dock). */
    handle: PostureLayoutSettings?,
    onHandleLongPress: (Offset) -> Unit,
    handleHidden: Boolean,
    handleAxis: HandleAxis,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    actions: HomeWorkspaceActions,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = layout.editMode,
        modifier = modifier,
        transitionSpec = {
            homePageOverviewMotionPolicy(reducedMotion).contentTransform(
                enteringOverview = targetState == HomeEditMode.ManagingPages,
                exitingOverview = initialState == HomeEditMode.ManagingPages,
            )
        },
        label = "home-page-overview",
    ) { editMode ->
        when (editMode) {
            HomeEditMode.Browsing ->
                if (handle != null) {
                    HomeBottomSearchArea(
                        pageCount = layout.pages.size,
                        selectedPageIndex = selectedPageIndex,
                        reducedMotion = reducedMotion,
                        onPageSelected = { pageIndex ->
                            layout.pages.getOrNull(pageIndex)?.let { page ->
                                actions.onAction(LauncherShellAction.SelectHomePage(page.id))
                            }
                        },
                        onIndicatorScrub = onIndicatorScrub,
                        onHandleLongPress = onHandleLongPress,
                        layout = handle,
                        axis = handleAxis,
                        actions = actions,
                        dock = layout.dock,
                        hidden = handleHidden,
                    )
                }

            is HomeEditMode.EditingPage ->
                PageEditControls(
                    pageCount = layout.pages.size,
                    selectedPageIndex = layout.selectedPageIndex,
                    onAction = actions.onAction,
                )

            HomeEditMode.ManagingPages ->
                PageOverviewControls(
                    layout = layout,
                    reducedMotion = reducedMotion,
                    appIconLoader = appIconLoader,
                    widgetViewFactory = widgetViewFactory,
                    onAction = actions.onAction,
                )
        }
    }
}

/**
 * The Home handle's row (ADR 0003, Amendment 1) above a bottom Dock: one small pill that carries the page dots, is the
 * way into search and keeps the quick-drag along the dots. The row is as tall as the handle is across it (a column of
 * dots, for pages that scroll up and down, makes it taller). The empty room around the pill is Home's background (its
 * long-press menu and its tap), and a long-press on the pill opens the same menu.
 */
@Composable
private fun HomeBottomSearchArea(
    pageCount: Int,
    selectedPageIndex: Int,
    reducedMotion: Boolean,
    onPageSelected: (Int) -> Unit,
    onIndicatorScrub: (Int) -> Unit,
    onHandleLongPress: (Offset) -> Unit,
    layout: PostureLayoutSettings,
    axis: HandleAxis,
    actions: HomeWorkspaceActions,
    dock: DockModel,
    hidden: Boolean = false,
) {
    val dots = layout.showsDots && HomeHandle.showsDots(pageCount)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HomeHandleLayout.reservedDp(DockPosition.BOTTOM, axis, pageCount, dots).dp),
        contentAlignment = Alignment.Center,
    ) {
        FadingHandle(hidden = hidden, reducedMotion = reducedMotion) {
            HomeHandlePill(
                pageCount = pageCount,
                selectedPageIndex = selectedPageIndex,
                reducedMotion = reducedMotion,
                haptics = actions.haptics,
                onOpenSearch = { actions.onAction(LauncherShellAction.OpenSearch) },
                onLongPress = onHandleLongPress,
                onPageSelected = onPageSelected,
                onLiveDragPageChanged = onIndicatorScrub,
                axis = axis,
                showsDots = layout.showsDots,
                dragEnabled = layout.dragsToScroll,
                dock = dock,
            )
        }
    }
}

/**
 * The Home handle beside a side Dock (ADR 0003, Amendment 2): a column between the Dock and the pages, as wide as the
 * handle, with the pill centred in it. The Dock is centred on its edge, so the pill is in front of the Dock's middle,
 * on Home's side of it, the way the folded handle stands above a bottom Dock. It runs the way the pages scroll.
 */
@Composable
private fun HomeHandleStrip(
    dockEdge: DockPosition,
    axis: HandleAxis,
    pageCount: Int,
    selectedPageIndex: Int,
    layout: PostureLayoutSettings,
    reducedMotion: Boolean,
    onPageSelected: (Int) -> Unit,
    onIndicatorScrub: (Int) -> Unit,
    onHandleLongPress: (Offset) -> Unit,
    actions: HomeWorkspaceActions,
    dock: DockModel,
    hidden: Boolean = false,
) {
    val dots = layout.showsDots && HomeHandle.showsDots(pageCount)
    Box(
        modifier =
            Modifier
                .fillMaxHeight()
                .width(HomeHandleLayout.reservedDp(dockEdge, axis, pageCount, dots).dp)
                .testTag(HOME_HANDLE_STRIP_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        FadingHandle(hidden = hidden, reducedMotion = reducedMotion) {
            HomeHandlePill(
                pageCount = pageCount,
                selectedPageIndex = selectedPageIndex,
                reducedMotion = reducedMotion,
                haptics = actions.haptics,
                onOpenSearch = { actions.onAction(LauncherShellAction.OpenSearch) },
                onLongPress = onHandleLongPress,
                onPageSelected = onPageSelected,
                onLiveDragPageChanged = onIndicatorScrub,
                axis = axis,
                showsDots = layout.showsDots,
                dragEnabled = layout.dragsToScroll,
                dock = dock,
            )
        }
    }
}

internal const val HOME_HANDLE_STRIP_TEST_TAG = "home-handle-strip"

@Composable
fun BoxScope.RemoveShortcutButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Remove $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "X",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private data class StandardHomeContentState(
    val layout: HomeLayout,
    val visibleLayout: HomeLayout,
    val dockPosition: DockPosition,
    val dragSession: HomeDragSession?,
    val widgetPickerDragPreview: WidgetPickerDragPlacementPreview?,
    val presentation: StandardHomePresentation,
    /** The room [HomeDockHost] takes along [dockPosition]'s edge, which the frame leaves empty. */
    val dockReservation: Dp,
)

private fun StandardHomeContentState.homeGridPresentation(actions: HomeWorkspaceActions): HomeGridPresentation =
    HomeGridPresentation(
        notificationGroupsByApp = presentation.notificationGroupsByApp,
        appShortcutsByApp = presentation.appShortcutsByApp,
        labelSettings = layout.settings.labels,
        reducedMotion = presentation.reducedMotion,
        widgetViewFactory = presentation.widgetViewFactory,
        libraryEnabled = presentation.libraryEnabled,
        dragTuning = presentation.dragTuning,
        generatedPage =
            GeneratedPagePresentation(
                notificationGroupsByApp = presentation.notificationGroupsByApp,
                notificationAccessStatus = presentation.notificationAccessStatus,
                installedApps = presentation.installedApps,
                onAction = actions.onAction,
            ),
    )

/** This drag as composition sees it: without where the finger is, which changes every frame. */
internal fun HomeDragSession.withoutPointer(): HomeDragSession =
    copy(pointerInRoot = Offset.Unspecified, dragOffsetX = 0f, dragOffsetY = 0f)

internal data class HomeDragSession(
    val item: LauncherItem,
    val originPageId: com.riffle.core.domain.launcher.home.LauncherPageId =
        com.riffle.core.domain.launcher.home.LauncherPageId("home"),
    val originCell: GridCell,
    val targetPageId: com.riffle.core.domain.launcher.home.LauncherPageId = originPageId,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
    val projectedCell: GridCell,
    /** Whether releasing right now would drop [item] on the dock instead of a home cell. */
    val isOverDock: Boolean = false,
    /** Over the Dock: the slot the finger hovers, where the item is pinned (null: at the end). */
    val dockIndex: Int? = null,
    /** Where the finger is, in the root; [Offset.Unspecified] until the first move. */
    val pointerInRoot: Offset = Offset.Unspecified,
    /** What dropping now would do on the target page (the push ring and dead zone); null: not planned. */
    val plan: HomeDragPlan? = null,
)

internal data class StandardHomeInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onDockInteractionExtentChanged: (Int) -> Unit = {},
    val onBottomControlsHeightChanged: (Int) -> Unit = {},
)

internal data class StandardHomePresentation(
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val installedApps: List<InstalledApp> = emptyList(),
    val appShortcutsByApp: AppShortcutsByApp,
    val homeGestures: HomeGestureSettings = HomeGestureSettings(),
    val reducedMotion: Boolean = false,
    val motionPerformanceTargetFps: MotionPerformanceTargetFps = MotionPerformanceTargetFps.FPS_120,
    val widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    val widgetPicker: StandardHomeWidgetPickerState = StandardHomeWidgetPickerState(),
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
    /** The layout of the posture Home is drawn in: which way the pages scroll and what the page dots do. */
    val layout: PostureLayoutSettings = PostureLayoutSettings.FOLDED_DEFAULT,
    /** Whether the Library is on; a generated page's "More apps" opens it, or search while it is off. */
    val libraryEnabled: Boolean = true,
    /**
     * The Stream's column stands over the handle's place (unfolded, ADR 0003, Amendment 2): the pill is not drawn,
     * so it
     * cannot show through or collide with the cards, and its room stays so the grid does not move. Folded, Home is gone
     * with the handle whenever the Stream is out.
     */
    val handleHidden: Boolean = false,
    /** The numbers of a Home drag (Settings > About > Developer options > Drag tuning). */
    val dragTuning: HomeDragTuning = HomeDragTuning(),
)

internal data class StandardHomeWidgetPickerState(
    val providers: List<InstalledWidgetProvider> = emptyList(),
    val profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> = emptyMap(),
    val catalogStatus: WidgetProviderCatalogStatus = WidgetProviderCatalogStatus.READY,
    val isOpen: Boolean = false,
    /** Whether this picker is placing onto the dock's panel rather than onto home or the dock. */
    val isTargetingDockPanel: Boolean = false,
)

internal data class HomeInsetPolicy(
    val reserveStatusBar: Boolean = true,
    val reserveNavigationBar: Boolean = true,
)

internal fun homeInsetPolicy(appearance: AppearanceSettings): HomeInsetPolicy =
    HomeInsetPolicy(
        reserveStatusBar = !appearance.homeSystemBars.statusBarHidden,
        reserveNavigationBar = !appearance.homeSystemBars.navigationBarHidden,
    )

@Composable
internal fun HomeInsetPolicy.safeDrawingInsets(): WindowInsets {
    var insets = WindowInsets.safeDrawing
    if (!reserveStatusBar) {
        insets = insets.exclude(WindowInsets.statusBars)
    }
    if (!reserveNavigationBar) {
        insets = insets.exclude(WindowInsets.navigationBars)
    }
    return insets
}

/**
 * The insets Home's own content (its grid, its handle, the paged Library standing in for it) lays out against: the
 * system bars and the cutout, never the keyboard. Home does not move, shrink or reflow for the keyboard: whatever the
 * keyboard serves (a reply field in a card, search) is lifted by its own surface, and Home stays as it is behind it.
 */
@Composable
internal fun HomeInsetPolicy.contentInsets(): WindowInsets {
    var insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    if (!reserveStatusBar) {
        insets = insets.exclude(WindowInsets.statusBars)
    }
    if (!reserveNavigationBar) {
        insets = insets.exclude(WindowInsets.navigationBars)
    }
    return insets
}

internal data class HomeGridPresentation(
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp,
    val labelSettings: HomeLabelSettings,
    val reducedMotion: Boolean = false,
    val widgetViewFactory: HomeWidgetViewFactory,
    val generatedPage: GeneratedPagePresentation = GeneratedPagePresentation(),
    /** Whether the Library is on, for a generated page's "More apps" button (search stands in while it is off). */
    val libraryEnabled: Boolean = true,
    /** The numbers of a Home drag (the folder hold). */
    val dragTuning: HomeDragTuning = HomeDragTuning(),
    /**
     * Which surface these cells belong to, so an item's own menu removes it from the right place.
     * The dock's panel renders through the same grid but is not one of the layout's pages.
     */
    val contextSurface: ShortcutContextSurface = ShortcutContextSurface.HOME,
)

internal data class GeneratedPagePresentation(
    val notificationGroupsByApp: List<AppNotificationGroup> = emptyList(),
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val installedApps: List<InstalledApp> = emptyList(),
    val onAction: (LauncherShellAction) -> Unit = {},
)

internal data class HomeItemDragState(
    val pageId: com.riffle.core.domain.launcher.home.LauncherPageId,
    val cell: GridCell,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val pageItems: List<LauncherItem>,
)

/** [latest], through one instance that never changes: each call goes to the latest actions given. */
@Composable
internal fun rememberStableWorkspaceActions(latest: HomeWorkspaceActions): HomeWorkspaceActions {
    val current = rememberUpdatedState(latest)
    return remember {
        HomeWorkspaceActions(
            onFolderOpen = { folder -> current.value.onFolderOpen(folder) },
            onFolderRename = { folder -> current.value.onFolderRename(folder) },
            onDragSessionChanged = { session -> current.value.onDragSessionChanged(session) },
            currentDragSession = { current.value.currentDragSession() },
            haptics =
                object : LauncherHaptics {
                    override fun longPress() = current.value.haptics.longPress()

                    override fun perform(event: LauncherHapticEvent) = current.value.haptics.perform(event)
                },
            onDockInteractionExtentChanged = { extent -> current.value.onDockInteractionExtentChanged(extent) },
            onDockAreaExtentChanged = { extent -> current.value.onDockAreaExtentChanged(extent) },
            onBottomControlsHeightChanged = { height -> current.value.onBottomControlsHeightChanged(height) },
            onWorkspaceGridBoundsChanged = { id, bounds -> current.value.onWorkspaceGridBoundsChanged(id, bounds) },
            onDockBoundsChanged = { bounds -> current.value.onDockBoundsChanged(bounds) },
            onBackgroundClick = { current.value.onBackgroundClick() },
            onDragNewPage = { current.value.onDragNewPage() },
            onAction = { action -> current.value.onAction(action) },
        )
    }
}

internal data class HomeWorkspaceActions(
    val onFolderOpen: (FolderItem) -> Unit,
    /** Opens the folder with its title in edit mode (a new folder; "Rename" in its menu). */
    val onFolderRename: (FolderItem) -> Unit = onFolderOpen,
    val onDragSessionChanged: (HomeDragSession?) -> Unit,
    val currentDragSession: () -> HomeDragSession? = { null },
    val haptics: LauncherHaptics,
    val onDockInteractionExtentChanged: (Int) -> Unit = {},
    /** The Dock's whole area along its cross axis, with the open card's actions row in it (the Stream's room). */
    val onDockAreaExtentChanged: (Int) -> Unit = {},
    val onBottomControlsHeightChanged: (Int) -> Unit = {},
    val onWorkspaceGridBoundsChanged: (LauncherPageId, Rect) -> Unit = { _, _ -> },
    val onDockBoundsChanged: (Rect) -> Unit = {},
    val onBackgroundClick: () -> Unit = {},
    /** A drag held at the trailing edge on the last page: offer a new empty page (see [HomeDragPages]). */
    val onDragNewPage: () -> Unit = {},
    val onAction: (LauncherShellAction) -> Unit,
)

private const val WIDGET_PICKER_EDGE_HOVER_ZONE_DP = 40
private const val WIDGET_PICKER_EDGE_HOVER_DELAY_MILLIS = 650L

internal fun widgetPickerDropCell(
    position: Offset,
    gridBounds: Rect,
    grid: GridDimensions,
): GridCell =
    GridCell(
        column = (((position.x - gridBounds.left) / gridBounds.width.coerceAtLeast(1f)) * grid.columns).toInt(),
        row = (((position.y - gridBounds.top) / gridBounds.height.coerceAtLeast(1f)) * grid.rows).toInt(),
    ).let { cell ->
        GridCell(
            column = cell.column.coerceIn(0, grid.columns - 1),
            row = cell.row.coerceIn(0, grid.rows - 1),
        )
    }

internal fun widgetPickerDropTarget(
    position: Offset,
    workspaceBounds: Rect,
    dockBounds: Rect?,
): WidgetAddTarget? =
    when {
        workspaceBounds.contains(position) -> WidgetAddTarget.HOME
        dockBounds?.contains(position) == true -> WidgetAddTarget.DOCK
        else -> null
    }

/**
 * The page handle going and coming with the Stream (unfolded, where it is hidden while the column stands): it fades on
 * the Dock's own spring rather than popping in the frame the mode changes (a snap under reduced motion). It stays
 * composed while it is still showing at all, and takes no touch once it has been asked to go. The fade is read where it
 * is drawn.
 */
@Composable
private fun FadingHandle(
    hidden: Boolean,
    reducedMotion: Boolean,
    content: @Composable () -> Unit,
) {
    val fade = remember { Animatable(if (hidden) 0f else 1f) }
    var composed by remember { mutableStateOf(!hidden) }
    if (!hidden) composed = true
    val tuning = LocalDockMotionTuning.current
    LaunchedEffect(hidden, reducedMotion) {
        if (hidden) {
            fade.animateTo(0f, RiffleMotion.smooth(tuning, reducedMotion, visibilityThreshold = 0.001f))
            composed = false
        } else {
            composed = true
            fade.animateTo(1f, RiffleMotion.smooth(tuning, reducedMotion, visibilityThreshold = 0.001f))
        }
    }
    if (composed) {
        Box(
            modifier =
                Modifier
                    .graphicsLayer { alpha = fade.value }
                    .then(if (hidden) Modifier.pointerInput(Unit) { detectTapGestures {} } else Modifier),
        ) { content() }
    }
}

/**
 * The page and cell of [page] under [point] in the root, given the page's grid at [gridBounds]; null where the point is
 * off the grid (or the page is generated, which takes no placed items).
 */
internal fun homeDropTargetAt(
    page: LauncherPage,
    gridBounds: Rect?,
    point: Offset,
): HomeDropTarget? {
    if (gridBounds == null || page.type is LauncherPageType.Generated || !gridBounds.contains(point)) return null
    val column = ((point.x - gridBounds.left) / (gridBounds.width / page.grid.columns)).toInt()
    val row = ((point.y - gridBounds.top) / (gridBounds.height / page.grid.rows)).toInt()
    val cell = GridCell(column.coerceIn(0, page.grid.columns - 1), row.coerceIn(0, page.grid.rows - 1))
    return HomeDropTarget(page.id, cell, free = page.items.none { item -> item.occupies(cell) })
}

/**
 * This drag with its [HomeDragPlan] for the page it is over: the pointer in cells of that page's grid (whose bounds in
 * the root are [gridBounds]), through [HomeDragPush]. Unplanned while the pointer, the bounds or the page are unknown,
 * or over the Dock. Dropping on top works on any page: on another page the item first joins it (the first free cell)
 * and the folder is made there.
 */

internal fun HomeDragSession.planned(
    layout: HomeLayout,
    gridBounds: Rect?,
    tuning: HomeDragTuning,
): HomeDragSession {
    val page = layout.pages.firstOrNull { it.id == targetPageId }
    if (gridBounds == null || pointerInRoot == Offset.Unspecified || page == null || isOverDock) {
        return copy(plan = null)
    }
    val x = (pointerInRoot.x - gridBounds.left) / (gridBounds.width / page.grid.columns)
    val y = (pointerInRoot.y - gridBounds.top) / (gridBounds.height / page.grid.rows)
    val numbers = tuning.coerced()
    val reserved = if (targetPageId == originPageId) setOf(originCell) else emptySet()
    val plan = HomeDragPush.plan(page, item, x, y, numbers, plan?.pushed?.keys.orEmpty(), reserved)
    return copy(plan = plan, projectedCell = plan.landing ?: originCell)
}

private fun homeGridPresentationFor(
    layout: HomeLayout,
    presentation: StandardHomePresentation,
    actions: HomeWorkspaceActions,
): HomeGridPresentation =
    HomeGridPresentation(
        notificationGroupsByApp = presentation.notificationGroupsByApp,
        appShortcutsByApp = presentation.appShortcutsByApp,
        labelSettings = layout.settings.labels,
        reducedMotion = presentation.reducedMotion,
        widgetViewFactory = presentation.widgetViewFactory,
        libraryEnabled = presentation.libraryEnabled,
        dragTuning = presentation.dragTuning,
        generatedPage = GeneratedPagePresentation(onAction = actions.onAction),
    )
