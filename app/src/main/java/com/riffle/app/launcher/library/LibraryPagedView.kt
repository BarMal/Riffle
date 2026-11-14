@file:Suppress("LongParameterList", "LongMethod")

package com.riffle.app.launcher.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppDrawerRowOverflowMenu
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.AppListSurface
import com.riffle.app.launcher.GridFadeInputBlock
import com.riffle.app.launcher.HomeGridLayoutMetrics
import com.riffle.app.launcher.HomeHandlePill
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.NotificationCountBadge
import com.riffle.app.launcher.RiffleContextMenu
import com.riffle.app.launcher.WallpaperReadableLabel
import com.riffle.app.launcher.appListEmptyText
import com.riffle.app.launcher.drawerKey
import com.riffle.app.launcher.drawerRowState
import com.riffle.app.launcher.gridFade
import com.riffle.app.launcher.notificationCountFor
import com.riffle.app.launcher.rememberGridFadeCell
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HandleStrip
import com.riffle.core.domain.launcher.home.HomeHandle
import com.riffle.core.domain.launcher.home.HomeHandleLayout
import com.riffle.core.domain.launcher.library.LibraryPaging
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import kotlinx.coroutines.launch

/**
 * The paged Library View (ADR 0003, Amendment 1): every app as an icon on horizontal pages, drawn straight over the
 * wallpaper with no card or sheet behind it. The grid has Home's columns and rows, cell size, icon size and labels, so
 * it reads as Home's sibling. The pages are the platform's pager; below them stands the Home handle, the same
 * component Home uses ([HomeHandlePill]): a tap searches, dragging along its dots quick-drags between pages.
 *
 * Long-pressing the empty space opens [backgroundMenu] (for the Library, "Show as"; for App library Home, its own
 * actions), and [backgroundActions] are the same routes for assistive technology. Tapping an app launches it, and
 * long-pressing it opens the Library's app menu.
 */
@Composable
internal fun LibraryPagedView(
    state: LibraryState,
    context: AppListContext,
    reducedMotion: Boolean,
    onOpenSearch: () -> Unit,
    backgroundActions: List<CustomAccessibilityAction>,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    /** The edge the Dock stands on: the handle stands just in from it (ADR 0003, Amendment 2). */
    dockEdge: DockPosition = DockPosition.BOTTOM,
    /** The Stream's column stands over the handle (unfolded, the app library as Home): the pill is not drawn. */
    handleHidden: Boolean = false,
    backgroundMenu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val columns = state.pagedGrid.columns.coerceAtLeast(1)
    val rows = state.pagedGrid.rows.coerceAtLeast(1)
    val pages = remember(state.apps, columns, rows) { LibraryPaging.pages(state.apps, columns, rows) }
    val pageCount = LibraryPaging.pageCount(state.apps.size, columns, rows)
    val pagerState = rememberPagerState { pageCount }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    val emptyText = appListEmptyText(AppListSurface.DRAWER, query = "", profileFilter = state.profileFilter)
    val menuLabel = stringResource(R.string.library_paged_background_label)
    val margins = state.pagedMargins
    // The pages scroll the way the posture says, and the handle follows them (ADR 0003, Amendment 2).
    val layout = state.pagedLayout
    val axis = HomeHandleLayout.axisFor(dockEdge, layout.pageScroll)
    val verticalPages = layout.pageScroll == PageScrollDirection.VERTICAL
    val strip = HomeHandleLayout.stripFor(dockEdge)
    val dots = layout.showsDots && HomeHandle.showsDots(pageCount)

    val pageContent: @Composable (Int) -> Unit = { index ->
        Box(modifier = Modifier.fillMaxSize().testTag("$LIBRARY_PAGE_TEST_TAG_PREFIX$index")) {
            // Behind the icons, so the space between them is the Library's own: a long-press there is its menu.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    context.haptics.longPress()
                                    menuOpen = true
                                },
                            )
                        }
                        .semantics {
                            onLongClick(label = menuLabel) {
                                menuOpen = true
                                true
                            }
                        },
            )
            if (pages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = emptyText, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LibraryPageGrid(
                    apps = pages.getOrElse(index) { emptyList() },
                    state = state,
                    context = context,
                )
            }
        }
    }
    val reservedDp = HomeHandleLayout.reservedDp(dockEdge, axis, pageCount, dots)
    // A column beside a side Dock stands in the pages' margin on the Dock's side first (the way the row above a bottom
    // Dock takes its height): the pages keep the rest of the margin and the grid lays out in what is left.
    val dockSideMargin = { margin: Int -> HomeHandleLayout.remainingMarginDp(margin, reservedDp) }
    val pagerModifier =
        Modifier
            .fillMaxSize()
            .padding(
                start = (if (strip == HandleStrip.LEFT_COLUMN) dockSideMargin(margins.start) else margins.start).dp,
                top = margins.top.dp,
                end = (if (strip == HandleStrip.RIGHT_COLUMN) dockSideMargin(margins.end) else margins.end).dp,
                bottom = margins.bottom.dp,
            )
    val pager: @Composable (Modifier) -> Unit = { outer ->
        if (verticalPages) {
            VerticalPager(
                state = pagerState,
                modifier = outer.then(pagerModifier),
                key = { index -> index },
            ) { index -> pageContent(index) }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = outer.then(pagerModifier),
                key = { index -> index },
            ) { index -> pageContent(index) }
        }
    }
    val handle: @Composable () -> Unit = {
        if (!handleHidden) {
            HomeHandlePill(
                pageCount = pageCount,
                selectedPageIndex = pagerState.currentPage,
                reducedMotion = reducedMotion,
                haptics = context.haptics,
                onOpenSearch = onOpenSearch,
                onPageSelected = { page ->
                    scope.launch {
                        if (reducedMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
                    }
                },
                onLiveDragPageChanged = { page -> scope.launch { pagerState.scrollToPage(page) } },
                axis = axis,
                showsDots = layout.showsDots,
                dragEnabled = layout.dragsToScroll,
                dock = state.dock,
            )
        }
    }
    val reserved = HomeHandleLayout.reservedDp(dockEdge, axis, pageCount, dots).dp

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(LIBRARY_PAGED_TEST_TAG)
                .semantics { customActions = backgroundActions },
    ) {
        if (strip == HandleStrip.BOTTOM_ROW) {
            Column(modifier = Modifier.fillMaxSize()) {
                pager(Modifier.weight(1f).fillMaxWidth())
                Spacer(modifier = Modifier.height(state.controlsSpacingDp.coerceAtLeast(0).dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(reserved).then(handleModifier),
                    contentAlignment = Alignment.Center,
                ) { handle() }
            }
        } else {
            // A column beside a side Dock: just in from the Dock, the handle centred in it, the pages the rest.
            val handleColumn: @Composable () -> Unit = {
                Box(
                    modifier = Modifier.fillMaxHeight().width(reserved).then(handleModifier),
                    contentAlignment = Alignment.Center,
                ) { handle() }
            }
            Row(modifier = Modifier.fillMaxSize()) {
                if (strip == HandleStrip.LEFT_COLUMN) handleColumn()
                pager(Modifier.weight(1f).fillMaxHeight())
                if (strip == HandleStrip.RIGHT_COLUMN) handleColumn()
            }
        }
        // The menu hangs off the view, not a page, so it survives the page changing under it.
        Box(modifier = Modifier.align(Alignment.BottomStart)) {
            RiffleContextMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                backgroundMenu { menuOpen = false }
            }
        }
    }
}

/** One page: Home's grid, sized the way Home sizes it (square cells that fit the room), apps in reading order. */
@Composable
private fun LibraryPageGrid(
    apps: List<InstalledApp>,
    state: LibraryState,
    context: AppListContext,
) {
    val metrics = remember { HomeGridLayoutMetrics() }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val gridSize =
            metrics.gridSizePx(
                grid = state.pagedGrid,
                maxWidthPx = with(density) { maxWidth.toPx() },
                maxHeightPx = with(density) { maxHeight.toPx() },
            )
        val cellSize = with(density) { gridSize.cellSizePx.toDp() }
        Column(
            modifier =
                Modifier
                    .requiredSize(with(density) { gridSize.widthPx.toDp() }, with(density) { gridSize.heightPx.toDp() })
                    .testTag(LIBRARY_PAGED_GRID_TEST_TAG),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(state.pagedGrid.rows) { row ->
                Row(modifier = Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    repeat(state.pagedGrid.columns) { column ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            apps.getOrNull(row * state.pagedGrid.columns + column)?.let { app ->
                                val fade = rememberGridFadeCell()
                                Box(
                                    modifier = Modifier.width(cellSize).fillMaxHeight().gridFade(fade),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LibraryAppCell(app = app, state = state, context = context)
                                    GridFadeInputBlock(fade, Modifier.matchParentSize())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An app as Home draws one: its icon at Home's icon size with the notification badge, and Home's label. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LibraryAppCell(
    app: InstalledApp,
    state: LibraryState,
    context: AppListContext,
) {
    val metrics = remember { HomeGridLayoutMetrics() }
    var menuOpen by remember(app.drawerKey) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val iconSize = state.labels.iconSizeDp.dp
    Column(
        modifier =
            Modifier
                .heightIn(min = metrics.homeItemContentHeightDp(state.labels).dp)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = { context.onAction(LauncherShellAction.LaunchApp(app.identity)) },
                    onLongClick = {
                        context.haptics.longPress()
                        menuOpen = true
                    },
                    onLongClickLabel = "Show ${app.label} actions",
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(iconSize)) {
            LauncherAppIcon(
                identity = app.identity,
                label = app.label,
                iconLoader = context.appIconLoader,
                modifier = Modifier.size(iconSize),
            )
            NotificationCountBadge(
                count = context.notificationCountFor(app.identity),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        WallpaperReadableLabel(text = app.label, settings = state.labels)
    }
    AppDrawerRowOverflowMenu(
        state = app.drawerRowState(context, showInlineActions = false),
        isExpanded = menuOpen,
        onExpandedChange = { expanded -> menuOpen = expanded },
        showButton = false,
        onAction = context.onAction,
    )
}

internal const val LIBRARY_PAGED_TEST_TAG = "library-paged"
internal const val LIBRARY_PAGED_GRID_TEST_TAG = "library-paged-grid"
internal const val LIBRARY_PAGE_TEST_TAG_PREFIX = "library-page-"
