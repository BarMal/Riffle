package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem

@Suppress("LongMethod")
@Composable
internal fun WorkspaceGrid(
    page: LauncherPage,
    gridState: HomeGridState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
    modifier: Modifier = Modifier,
    testTag: String = HOME_WORKSPACE_GRID_TEST_TAG,
) {
    // The same instances while they are equal: what a cell is given must not change for nothing (the layout is
    // rebuilt as it is filtered, so equal pages and presentations arrive as new objects).
    @Suppress("NAME_SHADOWING")
    val page = remember(page) { page }
    CountRecomposition(homeGridProbeKey(page.id.value))
    @Suppress("NAME_SHADOWING")
    val presentation = remember(presentation) { presentation }
    BoxWithConstraints(
        modifier = modifier,
    ) {
        // Behind the cells and everything else on the page, as big as the page: the room between and around the icons,
        // the page's own margins and an empty page are the background, whose long-press is the menu and tap the empty
        // tap. (The pager takes the touches of its whole page, so the page holds the handler itself.)
        HomeBackgroundContextMenu(
            haptics = actions.haptics,
            onAction = actions.onAction,
            items = backgroundContextMenuItems(presentation.contextSurface),
            longClickLabel = presentation.contextSurface.backgroundLongClickLabel,
            modifier = Modifier.matchParentSize(),
            onClick = actions.onBackgroundClick,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val metrics = HomeGridLayoutMetrics()
                val gridSize =
                    metrics.gridSizePx(
                        grid = page.grid,
                        maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() },
                        maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() },
                    )
                val cellSizePx = gridSize.cellSizePx
                val density = LocalDensity.current
                val cellSize = with(density) { cellSizePx.toDp() }
                val gridWidth = with(density) { gridSize.widthPx.toDp() }
                val gridHeight = with(density) { gridSize.heightPx.toDp() }
                val previewItems = page.itemsForDragPreview(gridState.dragSession)
                val activeDragSession =
                    gridState.dragSession
                        ?.takeIf { session -> session.originPageId == page.id || session.targetPageId == page.id }

                Column(
                    modifier =
                        Modifier
                            .requiredSize(gridWidth, gridHeight)
                            .onGloballyPositioned { coordinates ->
                                actions.onWorkspaceGridBoundsChanged(page.id, coordinates.boundsInRoot())
                            }
                            .testTag(testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(page.grid.rows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(page.grid.columns) { column ->
                                val cell = GridCell(column = column, row = row)
                                val source =
                                    activeDragSession?.takeIf { it.originPageId == page.id && it.originCell == cell }
                                val visibleItem = source?.item ?: previewItems.itemAt(cell = cell)
                                val landing =
                                    activeDragSession
                                        ?.takeIf { it.projectedCell == cell && it.targetPageId == page.id }
                                        ?.takeIf { it.plan?.dropOn == null }
                                        ?.let { session ->
                                            HomeCellLanding(
                                                placeholder =
                                                    HomeGridCellState(cell, previewItems)
                                                        .dragPlaceholderAtProjectedCell(session),
                                                ghostOf = (session.item as? AppShortcutItem)?.appIdentity,
                                            )
                                        }
                                // Everything a cell reads is its own: a drag recomposes only the cells it changes.
                                HomeGridCell(
                                    column = column,
                                    row = row,
                                    cellSize = cellSize,
                                    cellSizePx = cellSizePx,
                                    page = page,
                                    visibleItem = visibleItem,
                                    isDragSource = source != null,
                                    landing = landing,
                                    willMakeFolder =
                                        activeDragSession?.plan?.dropOn.let { it != null && it == visibleItem?.id },
                                    widgetPickerDragPreview =
                                        gridState.widgetPickerDragPreview
                                            ?.takeIf { preview -> preview.targetPageId == page.id }
                                            ?.takeIf { preview -> preview.cell == cell },
                                    isEditing = gridState.isEditing,
                                    presentation = presentation,
                                    appIconLoader = appIconLoader,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
            if (page.hasGeneratedContentOverflowAffordance) {
                GeneratedContentOverflowButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    overflowCount = page.generatedContentOverflowCount,
                    libraryEnabled = presentation.libraryEnabled,
                    onAction = actions.onAction,
                )
            }
        }
    }
}

/**
 * "More apps" on a generated page. It opens the Library; with the Library off (Simple mode, or its switch) the drawer
 * is not there to open, so it opens search, which finds the rest of the apps.
 */
@Composable
private fun GeneratedContentOverflowButton(
    modifier: Modifier,
    overflowCount: Int,
    libraryEnabled: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    // As wide as its label, so the room beside it is the page's background.
    TextButton(
        modifier = modifier.testTag(HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG),
        onClick = {
            onAction(if (libraryEnabled) LauncherShellAction.OpenAppDrawer else LauncherShellAction.OpenSearch)
        },
    ) {
        Text(text = "More apps ($overflowCount more)")
    }
}

private val LauncherPage.hasGeneratedContentOverflowAffordance: Boolean
    get() = type is LauncherPageType.Generated && generatedContentOverflowCount > 0

internal const val HOME_WORKSPACE_GRID_TEST_TAG = "home-workspace-grid"
internal const val HOME_WORKSPACE_GRID_OVERFLOW_TEST_TAG = "home-workspace-grid-overflow"

/** How far, in cells, an item still settles to its cell; further it lands (another page). */
private const val SETTLE_MAX_CELLS = 4f

/** The recomposition probe key of a whole Home page grid. */
internal fun homeGridProbeKey(pageId: String): String = "home-grid:$pageId"

/** The recomposition probe key of one cell of a Home page: a drag must recompose only the cells it touches. */
internal fun homeGridCellProbeKey(
    pageId: String,
    cell: GridCell,
): String = "home-cell:$pageId:${cell.column},${cell.row}"

/** One cell of Home's placed grid (every cell, filled or not), for a test to measure the grid by. */
internal const val HOME_GRID_CELL_TEST_TAG = "home-grid-cell"

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun RowScope.HomeGridCell(
    column: Int,
    row: Int,
    cellSize: Dp,
    cellSizePx: Float,
    page: LauncherPage,
    visibleItem: LauncherItem?,
    isDragSource: Boolean,
    landing: HomeCellLanding?,
    willMakeFolder: Boolean,
    widgetPickerDragPreview: WidgetPickerDragPlacementPreview?,
    isEditing: Boolean,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    // The coordinates, not a GridCell: a domain class is unstable, so a fresh one at each call would never be skipped.
    val cell = remember(column, row) { GridCell(column = column, row = row) }
    CountRecomposition(homeGridCellProbeKey(page.id.value, cell))
    Box(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(HOME_GRID_CELL_TEST_TAG),
        contentAlignment = Alignment.Center,
    ) {
        val fade = rememberGridFadeCell()
        Box(
            modifier =
                Modifier
                    .width(cellSize)
                    .fillMaxHeight()
                    // Flows into the fade under the Stream's column when one stands; otherwise nothing.
                    .gridFade(fade),
            contentAlignment = Alignment.Center,
        ) {
            landing?.placeholder?.let { placeholder ->
                HomeDragPlaceholder(
                    span = placeholder.span,
                    cellSize = cellSize,
                    cellSizePx = cellSizePx,
                    fillSpan = placeholder.fillSpan,
                    iconSizeDp = presentation.labelSettings.iconSizeDp,
                    ghostOf = landing.ghostOf,
                    appIconLoader = appIconLoader,
                )
            }

            val folderState =
                rememberFolderWillForm(
                    active = willMakeFolder,
                    holdMillis = presentation.dragTuning.folderHoldMillis,
                    reducedMotion = presentation.reducedMotion,
                )
            visibleItem?.let { item ->
                key(item.id.value) {
                    Box(
                        modifier =
                            Modifier
                                .graphicsLayer {
                                    scaleX = folderState.scale
                                    scaleY = folderState.scale
                                }
                                .folderWillFormOutline(folderState.armed, presentation.labelSettings.iconSizeDp)
                                .homeItemSettle(
                                    id = item.id,
                                    isDragSource = isDragSource,
                                    reducedMotion = presentation.reducedMotion,
                                    maxDistancePx = cellSizePx * SETTLE_MAX_CELLS,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        HomeGridItem(
                            item = item,
                            state =
                                HomeGridItemState(
                                    cell = cell,
                                    cellSize = cellSize,
                                    cellSizePx = cellSizePx,
                                    grid = page.grid,
                                    pageId = page.id,
                                    pageItems = page.items,
                                    isEditing = isEditing,
                                    isDragSource = isDragSource,
                                ),
                            presentation = presentation,
                            appIconLoader = appIconLoader,
                            actions = actions,
                        )
                    }
                }
            }

            widgetPickerDragPreview?.let { preview ->
                WidgetPickerDragPlaceholder(preview = preview, cellSize = cellSize)
            }
            // A cell faded out under the column or behind an open card takes no input.
            GridFadeInputBlock(fade, Modifier.matchParentSize())
        }
    }
}

/** The landing marker of a drag in the cell it would land in: its placeholder and the icon it is the outline of. */
internal data class HomeCellLanding(
    val placeholder: HomeDragPlaceholderState?,
    val ghostOf: AppIdentity?,
)

/** What a landing cell needs to look at the page's items under the drag preview (the placeholder's occupant). */
internal data class HomeGridCellState(
    val cell: GridCell,
    val previewItems: List<LauncherItem>,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeGridItem(
    item: LauncherItem,
    state: HomeGridItemState,
    presentation: HomeGridPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    when (item) {
        is AppShortcutItem ->
            HomeShortcut(
                shortcut = item,
                dragState =
                    HomeItemDragState(
                        pageId = state.pageId,
                        cell = state.cell,
                        cellSizePx = state.cellSizePx,
                        grid = state.grid,
                        pageItems = state.pageItems,
                    ),
                isEditing = state.isEditing,
                isDragSource = state.isDragSource,
                modifier = state.dragSourceModifier,
                presentation =
                    HomeShortcutPresentation(
                        notificationCount = presentation.notificationGroupsByApp.notificationCountFor(item),
                        appShortcuts = presentation.appShortcutsByApp[item.appIdentity].orEmpty(),
                        labelSettings = presentation.labelSettings,
                        reducedMotion = presentation.reducedMotion,
                        contextSurface = presentation.contextSurface,
                    ),
                appIconLoader = appIconLoader,
                actions = actions,
            )

        is FolderItem ->
            Box(modifier = state.dragSourceModifier.fillMaxSize()) {
                HomeFolder(
                    isDragSource = state.isDragSource,
                    folder = item,
                    dragState =
                        HomeItemDragState(
                            pageId = state.pageId,
                            cell = state.cell,
                            cellSizePx = state.cellSizePx,
                            grid = state.grid,
                            pageItems = state.pageItems,
                        ),
                    isEditing = state.isEditing,
                    presentation =
                        HomeFolderPresentation(
                            notificationCount = presentation.notificationGroupsByApp.notificationCountFor(item),
                            labelSettings = presentation.labelSettings,
                            reducedMotion = presentation.reducedMotion,
                        ),
                    appIconLoader = appIconLoader,
                    actions = actions,
                )
            }

        is WidgetItem ->
            HomeGridWidget(
                widget = item,
                state = state,
                presentation = presentation,
                actions = actions,
            )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeGridWidget(
    widget: WidgetItem,
    state: HomeGridItemState,
    presentation: HomeGridPresentation,
    actions: HomeWorkspaceActions,
) {
    val span = widget.placement?.span ?: GridSpan()

    Box(
        modifier =
            state.dragSourceModifier
                .requiredWidth(state.cellSize * span.columns)
                .requiredHeight(state.cellSize * span.rows)
                .graphicsLayer {
                    translationX = ((span.columns - 1) * state.cellSizePx) / 2f
                    translationY = ((span.rows - 1) * state.cellSizePx) / 2f
                },
    ) {
        // Lifted, the widget leaves the outline of its span (the widget itself stays composed, unseen, to keep the
        // gesture it started).
        if (state.isDragSource) WidgetSpanOutline(modifier = Modifier.matchParentSize())
        Box(modifier = Modifier.fillMaxSize().then(if (state.isDragSource) Modifier.alpha(0f) else Modifier)) {
            HomeWidgetPlaceholder(
                widget = widget,
                isEditing = state.isEditing,
                contextSurface = presentation.contextSurface,
                widgetViewFactory = presentation.widgetViewFactory,
                dragState =
                    HomeItemDragState(
                        pageId = state.pageId,
                        cell = state.cell,
                        cellSizePx = state.cellSizePx,
                        grid = state.grid,
                        pageItems = state.pageItems,
                    ),
                workspaceActions = actions,
                onAction = actions.onAction,
            )
        }
    }
}

@Suppress("LongMethod", "LongParameterList")
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeShortcut(
    shortcut: AppShortcutItem,
    dragState: HomeItemDragState,
    isEditing: Boolean,
    isDragSource: Boolean,
    modifier: Modifier,
    presentation: HomeShortcutPresentation,
    appIconLoader: AppIconLoader,
    actions: HomeWorkspaceActions,
) {
    val metrics = HomeGridLayoutMetrics()
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }
    val pressInteractionSource = remember { MutableInteractionSource() }
    val pressMotionPolicy = homeIconPressMotionPolicy(presentation.reducedMotion)
    val pressIndication =
        if (pressMotionPolicy.usesDefaultPressIndication) LocalIndication.current else null
    val pressHandlers =
        homeShortcutPressHandlers(
            isEditing = isEditing,
            onShowContextMenu = { isContextMenuExpanded.value = true },
            onLaunch = { actions.onAction(shortcut.launchAction()) },
        )
    val longClickLabel =
        if (isEditing) {
            "Show ${shortcut.label} actions"
        } else {
            "Edit ${shortcut.label}"
        }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .heightIn(min = metrics.homeItemContentHeightDp(presentation.labelSettings).dp)
                    .homeIconPressMotion(
                        interactionSource = pressInteractionSource,
                        policy = pressMotionPolicy,
                    )
                    .combinedClickable(
                        enabled = true,
                        interactionSource = pressInteractionSource,
                        indication = pressIndication,
                        onClick = pressHandlers.onTap,
                        onLongClickLabel = longClickLabel,
                    )
                    .homeItemDrag(
                        enabled = true,
                        item = shortcut,
                        dragState = dragState,
                        actions = actions,
                        onStationaryLongPress = pressHandlers.onLongPress,
                        onMenuDismiss = { isContextMenuExpanded.value = false },
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp)) {
                if (isDragSource) {
                    // The lifted icon is drawn above everything; this is the desaturated, dimmed ghost it left.
                    HomeGhostIcon(
                        identity = shortcut.appIdentity,
                        iconLoader = appIconLoader,
                        outline = false,
                        modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp),
                    )
                } else {
                    LauncherAppIcon(
                        identity = shortcut.appIdentity,
                        label = shortcut.label,
                        iconLoader = appIconLoader,
                        modifier = Modifier.size(presentation.labelSettings.iconSizeDp.dp),
                    )
                }
                if (!isEditing) {
                    NotificationCountBadge(
                        count = presentation.notificationCount,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            WallpaperReadableLabel(
                text = shortcut.label,
                settings = presentation.labelSettings,
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                shortcutContextMenuItems(
                    shortcut = shortcut,
                    surface = presentation.contextSurface,
                    appShortcuts = presentation.appShortcuts,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = actions.onAction,
        )
    }
}

internal data class HomeGridState(
    val isEditing: Boolean,
    val pageCount: Int,
    val selectedPageIndex: Int,
    val dragSession: HomeDragSession?,
    val widgetPickerDragPreview: WidgetPickerDragPlacementPreview? = null,
)

internal data class HomeGridItemState(
    val pageId: com.riffle.core.domain.launcher.home.LauncherPageId,
    val cell: GridCell,
    val cellSize: Dp,
    val cellSizePx: Float,
    val grid: GridDimensions,
    val pageItems: List<LauncherItem>,
    val isEditing: Boolean,
    /** Whether this item is the one lifted: it stays where it was (as a ghost); the lifted icon is drawn above. */
    val isDragSource: Boolean,
) {
    val dragSourceModifier: Modifier = Modifier
}

private data class HomeShortcutPresentation(
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
    val labelSettings: HomeLabelSettings,
    val reducedMotion: Boolean,
    val contextSurface: ShortcutContextSurface,
)
