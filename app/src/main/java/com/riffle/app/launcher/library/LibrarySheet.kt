@file:Suppress("LongParameterList", "LongMethod", "TooManyFunctions", "CyclomaticComplexMethod")
@file:OptIn(ExperimentalFoundationApi::class)

package com.riffle.app.launcher.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconGrid
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.AppListSurface
import com.riffle.app.launcher.AppProfileFilterChips
import com.riffle.app.launcher.AppSearchField
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.RiffleContextMenu
import com.riffle.app.launcher.SearchGridResult
import com.riffle.app.launcher.SearchResultList
import com.riffle.app.launcher.ShowAsMenuItems
import com.riffle.app.launcher.appListEmptyText
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.launcherPanelContentColor
import com.riffle.app.launcher.launcherPanelSurfaceColor
import com.riffle.app.launcher.searchEmptyText
import com.riffle.app.launcher.searchWebPreview
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import com.riffle.core.domain.launcher.dockpull.DockPullThresholds
import com.riffle.core.domain.launcher.dockpull.SettleOutcome
import com.riffle.core.domain.launcher.library.LibraryCategories
import com.riffle.core.domain.launcher.library.LibraryHit
import com.riffle.core.domain.launcher.library.LibraryView
import kotlinx.coroutines.launch

/**
 * The Library (ADR 0003): a Thick glass sheet that rises over Home or the Stream, the Dock still drawn under it.
 *
 * It slides in along [direction] (away from the Dock's edge). While a Dock pull runs, [awayFraction] says how far the
 * sheet still is from open and the finger moves it 1:1; opened any other way it springs in on its own; under reduced
 * motion it fades. A pull toward the Dock's edge on the header closes it (the same commit thresholds as the Stream
 * pull), with Back, Home, "Close Library" and launching an app as the other ways out.
 *
 * Inside: the header (long-press or the options button for "Show as", with Undo), the search field, the profile chips
 * and the stored view, or the results while the field has text. Every action goes out through [context].
 */
@Composable
internal fun LibrarySheet(
    state: LibraryState,
    context: AppListContext,
    reducedMotion: Boolean,
    direction: DockPullDirection,
    awayFraction: () -> Float?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val latestState by rememberUpdatedState(state)
    var openCategoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var dismissFraction by remember { mutableFloatStateOf(0f) }
    var extentPx by remember { mutableFloatStateOf(1f) }
    // A sheet that opens under a running pull is already where the finger put it; any other way in springs it up.
    val enter = remember { Animatable(if (awayFraction() != null) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enter.value > 0f) enter.animateTo(0f, RiffleMotion.smooth(reducedMotion, ENTER_THRESHOLD))
    }
    val vertical = direction.unitY != 0f
    val categories = remember(state.apps) { LibraryCategories.from(state.apps) }
    val openCategory = categories.firstOrNull { category -> category.key == openCategoryKey }
    val onAction = context.onAction

    BackHandler(enabled = state.isSearching) { onAction(LauncherShellAction.SearchQueryChanged("")) }
    BackHandler(enabled = openCategory != null && !state.isSearching) { openCategoryKey = null }

    val undo = stringResource(R.string.stream_undo)
    val resources = LocalContext.current.resources
    val onShowAs: (LibraryView) -> Unit = { view ->
        val before = latestState.view
        if (view != before) {
            onAction(LauncherShellAction.SelectLibraryView(view))
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val outcome =
                    snackbar.showSnackbar(
                        message = resources.getString(R.string.library_shown_as, resources.getString(view.titleRes())),
                        actionLabel = undo,
                        duration = SnackbarDuration.Long,
                    )
                if (outcome == SnackbarResult.ActionPerformed) onAction(LauncherShellAction.SelectLibraryView(before))
            }
        }
    }

    val density = LocalDensity.current.density
    val dragState =
        rememberDraggableState { delta ->
            // Toward the Dock's edge closes: the opposite of the pull's own direction.
            val toEdge = -delta * (if (vertical) direction.unitY else direction.unitX)
            dismissFraction = (dismissFraction + toEdge / extentPx).coerceIn(0f, 1f)
        }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = LibraryMaxWidth)
                    .fillMaxSize()
                    .onSizeChanged {
                            size ->
                        extentPx = (if (vertical) size.height else size.width).toFloat().coerceAtLeast(1f)
                    }
                    .graphicsLayer {
                        val away = awayFraction() ?: maxOf(enter.value, dismissFraction)
                        if (reducedMotion) {
                            alpha = 1f - away
                        } else {
                            translationX = -direction.unitX * away * size.width
                            translationY = -direction.unitY * away * size.height
                        }
                    }
                    .testTag(LIBRARY_SHEET_TEST_TAG),
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                weight = GlassWeight.Thick,
                shape = RiffleShapes.extraLarge,
                tint = launcherPanelSurfaceColor(),
                contentColor = launcherPanelContentColor(),
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = RiffleSpacing.l)) {
                    LibraryHeader(
                        view = state.view,
                        onShowAs = onShowAs,
                        onClose = onClose,
                        modifier =
                            Modifier.draggable(
                                state = dragState,
                                orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
                                onDragStopped = { velocityPxPerSecond ->
                                    val axisUnit = if (vertical) direction.unitY else direction.unitX
                                    val toEdgeVelocityDp = -velocityPxPerSecond * axisUnit / density
                                    val outcome =
                                        DockPullThresholds().outcomeFor(
                                            progress = dismissFraction,
                                            gestureDistanceDp = dismissFraction * extentPx / density,
                                            velocityDpPerSecond = toEdgeVelocityDp,
                                        )
                                    val target = if (outcome == SettleOutcome.COMMIT) 1f else 0f
                                    scope.launch {
                                        Animatable(dismissFraction).animateTo(
                                            target,
                                            RiffleMotion.smooth(reducedMotion, ENTER_THRESHOLD),
                                        ) { dismissFraction = value }
                                        if (target == 1f) onClose()
                                    }
                                },
                            ),
                    )
                    LibrarySearchRow(state = state, onAction = onAction)
                    if (!state.isSearching && openCategory == null) {
                        AppProfileFilterChips(
                            selectedFilter = state.profileFilter,
                            onFilterSelected = { filter ->
                                onAction(LauncherShellAction.AppDrawerProfileFilterSelected(filter))
                            },
                            apps = state.installedApps,
                        )
                    }
                    Spacer(modifier = Modifier.height(RiffleSpacing.s))
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LibraryBody(
                            state = state,
                            context = context,
                            categories = categories,
                            openCategory = openCategory,
                            onOpenCategory = { category -> openCategoryKey = category.key },
                            onCloseCategory = { openCategoryKey = null },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(RiffleSpacing.l),
            )
        }
    }
}

@Composable
private fun LibraryBody(
    state: LibraryState,
    context: AppListContext,
    categories: List<com.riffle.core.domain.launcher.library.LibraryCategory>,
    openCategory: com.riffle.core.domain.launcher.library.LibraryCategory?,
    onOpenCategory: (com.riffle.core.domain.launcher.library.LibraryCategory) -> Unit,
    onCloseCategory: () -> Unit,
) {
    val emptyText =
        appListEmptyText(AppListSurface.DRAWER, query = "", profileFilter = state.profileFilter)
    when {
        state.isSearching ->
            SearchResultList(
                results = state.hits.map(LibraryHit::toGridResult),
                appListContext = context,
                emptyText = searchEmptyText(state.query, state.filters),
                presentation = state.searchPresentation,
                gridColumns = state.searchGridColumns,
                webPreview = searchWebPreview(state.query),
                modifier = Modifier.fillMaxSize(),
            )

        openCategory != null ->
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCloseCategory, modifier = Modifier.size(LibraryMinTarget)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.library_category_back),
                        )
                    }
                    Text(
                        text = openCategory.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                AppIconGrid(
                    apps = openCategory.apps,
                    columns = state.iconGridColumns,
                    emptyText = emptyText,
                    context = context,
                    modifier = Modifier.weight(1f),
                )
            }

        state.apps.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = emptyText, style = MaterialTheme.typography.bodyLarge)
            }

        else ->
            when (state.view) {
                LibraryView.Categories ->
                    LibraryCategoriesView(categories = categories, context = context, onOpen = onOpenCategory)

                LibraryView.AlphaList -> LibraryAlphaListView(apps = state.apps, context = context)

                LibraryView.Grid ->
                    AppIconGrid(
                        apps = state.apps,
                        columns = state.iconGridColumns,
                        emptyText = emptyText,
                        context = context,
                    )
            }
    }
}

/** A result row drawn by the shared results panel (apps, app shortcuts and settings, in [LibraryHit] order). */
internal fun LibraryHit.toGridResult(): SearchGridResult =
    when (this) {
        is LibraryHit.App -> SearchGridResult.App(app)
        is LibraryHit.Shortcut -> SearchGridResult.Shortcut(shortcut)
        is LibraryHit.Setting -> SearchGridResult.Setting(result)
    }

/** The search field: Enter launches the top hit; swipe down on Home or `OPEN_SEARCH` focuses it, keyboard up. */
@Composable
private fun LibrarySearchRow(
    state: LibraryState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.focusSearch) {
        if (state.focusSearch) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val latestHits by rememberUpdatedState(state.hits)
    AppSearchField(
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(LIBRARY_SEARCH_FIELD_TEST_TAG),
        query = state.query,
        onQueryChanged = { value -> onAction(LauncherShellAction.SearchQueryChanged(value)) },
        label = stringResource(R.string.library_search_label),
        onSearch = { latestHits.firstOrNull()?.toGridResult()?.action?.let(onAction) },
    )
}

/**
 * The header: the title, an options button and, on long-press, the "Show as" menu; a pull toward the Dock's edge on it
 * closes the sheet. One custom-actions list carries "Close Library" and each view for assistive technology.
 */
@Composable
private fun LibraryHeader(
    view: LibraryView,
    onShowAs: (LibraryView) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val title = stringResource(R.string.library_title)
    val close = stringResource(R.string.library_close_action)
    val resources = LocalContext.current.resources
    val showAsActions =
        LibraryView.entries.map { option ->
            val label = resources.getString(R.string.stream_show_as) + " " + resources.getString(option.titleRes())
            CustomAccessibilityAction(label) {
                onShowAs(option)
                true
            }
        }
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onLongClickLabel = stringResource(R.string.library_header_long_press_label),
                        onLongClick = { menuOpen = true },
                        onClick = {},
                    )
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(close) {
                                onClose()
                                true
                            },
                        ) + showAsActions
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = RiffleSpacing.s)
                        .size(width = LibraryHandleWidth, height = LibraryHandleHeight)
                        .background(MaterialTheme.colorScheme.outline, RiffleShapes.full)
                        .semantics { contentDescription = resources.getString(R.string.library_close_handle) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = RiffleSpacing.s).semantics { heading() },
                )
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(LibraryMinTarget)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.library_options))
                }
            }
        }
        RiffleContextMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ShowAsMenuItems(
                options = LibraryView.entries,
                current = view,
                optionLabel = { option -> stringResource(option.titleRes()) },
                onSelect = { option ->
                    menuOpen = false
                    onShowAs(option)
                },
            )
        }
    }
}

internal fun LibraryView.titleRes(): Int =
    when (this) {
        LibraryView.Categories -> R.string.library_view_categories
        LibraryView.AlphaList -> R.string.library_view_alpha_list
        LibraryView.Grid -> R.string.library_view_grid
    }

private val LibraryMaxWidth = 840.dp
private val LibraryHandleWidth = 36.dp
private val LibraryHandleHeight = 4.dp
private const val ENTER_THRESHOLD = 0.001f
internal const val LIBRARY_SHEET_TEST_TAG = "library-sheet"
internal const val LIBRARY_SEARCH_FIELD_TEST_TAG = "library-search-field"
