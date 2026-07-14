@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPageType

@Composable
fun PageEditControls(
    pageCount: Int,
    selectedPageIndex: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            enabled = selectedPageIndex > 0,
            onClick = { onAction(LauncherShellAction.SelectPreviousHomePage) },
        ) {
            Text(text = "Previous")
        }
        TextButton(onClick = { isPageMenuExpanded.value = true }) {
            Text(text = "Page ${selectedPageIndex + 1} / $pageCount")
        }
        ShortcutContextMenu(
            expanded = isPageMenuExpanded.value,
            items =
                pageManagementMenuItems(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                ),
            onDismissRequest = { isPageMenuExpanded.value = false },
            onAction = onAction,
        )
        TextButton(
            enabled = selectedPageIndex < pageCount - 1,
            onClick = { onAction(LauncherShellAction.SelectNextHomePage) },
        ) {
            Text(text = "Next")
        }
        TextButton(onClick = { onAction(LauncherShellAction.ExitHomeEditMode) }) {
            Text(text = "Done")
        }
    }
}

@Composable
fun PageOverviewControls(
    layout: HomeLayout,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = remember { mutableStateOf(false) }
    val selectedPage = layout.selectedPage

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageOverviewStrip(
            layout = layout,
            reducedMotion = reducedMotion,
            appIconLoader = appIconLoader,
            widgetViewFactory = widgetViewFactory,
            onAction = onAction,
        )
        PageTypeControls(
            selectedType = selectedPage.type,
            onAction = onAction,
        )
        PageGridControls(
            selectedGrid = selectedPage.grid,
            onAction = onAction,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { onAction(LauncherShellAction.EnterHomeEditMode) }) {
                Text(text = "Edit page")
            }
            OutlinedButton(onClick = { onAction(LauncherShellAction.AddHomePage) }) {
                Text(text = "Add page")
            }
            OutlinedButton(onClick = { onAction(LauncherShellAction.DuplicateSelectedHomePage) }) {
                Text(text = "Duplicate page")
            }
            pageOverviewPinActionLabel(
                type = selectedPage.type,
                isPinned = selectedPage.isPinned,
            )?.let { label ->
                OutlinedButton(onClick = { onAction(LauncherShellAction.ToggleSelectedHomePagePinned) }) {
                    Text(text = label)
                }
            }
            TextButton(onClick = { isPageMenuExpanded.value = true }) {
                Text(text = "More")
            }
            TextButton(onClick = { onAction(LauncherShellAction.ExitHomeEditMode) }) {
                Text(text = "Done")
            }
            ShortcutContextMenu(
                expanded = isPageMenuExpanded.value,
                items =
                    pageManagementMenuItems(
                        pageCount = layout.pages.size,
                        selectedPageIndex = layout.selectedPageIndex,
                        includeOverview = false,
                        includeCreationActions = false,
                    ),
                onDismissRequest = { isPageMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PageOverviewStrip(
    layout: HomeLayout,
    reducedMotion: Boolean,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    onAction: (LauncherShellAction) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(PAGE_OVERVIEW_STRIP_TEST_TAG),
        contentPadding = PaddingValues(horizontal = PAGE_OVERVIEW_CONTENT_PADDING_DP.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_CARD_SPACING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = layout.pages,
            key = { _, page -> page.id.value },
        ) { index, page ->
            PageOverviewCard(
                state =
                    PageOverviewCardState(
                        index = index,
                        pageCount = layout.pages.size,
                        page = page,
                        isSelected = page.id == layout.selectedPageId,
                    ),
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetViewFactory,
                reducedMotion = reducedMotion,
                onClick = { onAction(LauncherShellAction.SelectHomePage(page.id)) },
                onAction = onAction,
                onMoveToIndex = { targetIndex ->
                    onAction(LauncherShellAction.MoveHomePage(pageId = page.id, targetIndex = targetIndex))
                },
            )
        }
    }
}

@Composable
private fun PageOverviewCard(
    state: PageOverviewCardState,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    onMoveToIndex: (Int) -> Unit,
) {
    val isMenuExpanded = remember(state.page.id) { mutableStateOf(false) }

    Surface(
        modifier =
            Modifier
                .width(PAGE_OVERVIEW_CARD_WIDTH_DP.dp)
                .pageOverviewReflow(state = state, reducedMotion = reducedMotion)
                .pageOverviewReorderDrag(state = state, onMoveToIndex = onMoveToIndex)
                .clip(LocalLauncherCardShape.current)
                .testTag(pageOverviewCardTestTag(state.page.id.value))
                .clickable(onClick = onClick),
        shape = LocalLauncherCardShape.current,
        color =
            if (state.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (state.isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        tonalElevation = if (state.isSelected) 4.dp else 1.dp,
        border =
            if (state.isSelected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PageOverviewCardHeader(
                state = state,
                onMoreClick = {
                    onAction(LauncherShellAction.SelectHomePage(state.page.id))
                    isMenuExpanded.value = true
                },
            )
            Text(
                text = state.page.type.pageOverviewTypeLabel,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "Press and hold, then drag to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PageOverviewPreview(
                page = state.page,
                appIconLoader = appIconLoader,
                widgetViewFactory = widgetViewFactory,
            )
            ShortcutContextMenu(
                expanded = isMenuExpanded.value,
                items = pageOverviewCardMenuItems(index = state.index, pageCount = state.pageCount),
                onDismissRequest = { isMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PageOverviewCardHeader(
    state: PageOverviewCardState,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pageOverviewLabel(index = state.index),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = onMoreClick) {
            Text(text = "More")
        }
    }
}

@Composable
private fun PageGridControls(
    selectedGrid: GridDimensions,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Page grid",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pageGridDimensionOptions(selectedGrid).forEach { option ->
                FilterChip(
                    selected = option.dimensions == selectedGrid,
                    onClick = {
                        onAction(LauncherShellAction.SelectSelectedHomePageGridDimensions(option.dimensions))
                    },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

@Composable
private fun PageTypeControls(
    selectedType: LauncherPageType,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Page type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pageTypeOptions.forEach { option ->
                FilterChip(
                    selected = option.type == selectedType,
                    onClick = {
                        onAction(LauncherShellAction.SelectSelectedHomePageType(option.type))
                    },
                    label = { Text(text = option.label) },
                )
            }
        }
    }
}

internal fun pageManagementMenuItems(
    pageCount: Int,
    selectedPageIndex: Int,
    includeOverview: Boolean = true,
    includeCreationActions: Boolean = true,
): List<ShortcutContextMenuItem> =
    listOfNotNull(
        if (includeCreationActions) {
            ShortcutContextMenuItem(
                label = "Add page",
                action = LauncherShellAction.AddHomePage,
            )
        } else {
            null
        },
        overviewMenuItem(includeOverview),
        if (includeCreationActions) {
            ShortcutContextMenuItem(
                label = "Duplicate page",
                action = LauncherShellAction.DuplicateSelectedHomePage,
            )
        } else {
            null
        },
        ShortcutContextMenuItem(
            label = "Move page left",
            action = LauncherShellAction.MoveSelectedHomePageLeft,
            enabled = selectedPageIndex > 0,
        ),
        ShortcutContextMenuItem(
            label = "Move page right",
            action = LauncherShellAction.MoveSelectedHomePageRight,
            enabled = selectedPageIndex < pageCount - 1,
        ),
        ShortcutContextMenuItem(
            label = "Delete page",
            action = LauncherShellAction.DeleteSelectedHomePage,
            enabled = pageCount > 1,
        ),
    )

private fun overviewMenuItem(includeOverview: Boolean): ShortcutContextMenuItem? =
    if (includeOverview) {
        ShortcutContextMenuItem(
            label = "Manage pages",
            action = LauncherShellAction.EnterHomePageOverview,
        )
    } else {
        null
    }

internal fun pageOverviewCardMenuItems(
    index: Int,
    pageCount: Int,
): List<ShortcutContextMenuItem> =
    pageManagementMenuItems(
        pageCount = pageCount,
        selectedPageIndex = index,
        includeOverview = false,
    )

@Composable
fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == selectedPageIndex
            val width =
                animateDpAsState(
                    targetValue = if (isSelected) 18.dp else 6.dp,
                    label = "page-indicator-width",
                )
            val color =
                animateColorAsState(
                    targetValue = pageIndicatorColor(isSelected = isSelected),
                    label = "page-indicator-color",
                )

            Box(
                modifier =
                    Modifier
                        .width(width.value)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(color.value),
            )
        }
    }
}

@Composable
private fun pageIndicatorColor(isSelected: Boolean) =
    if (isSelected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    }

private fun pageOverviewLabel(index: Int): String = "Page ${index + 1}"

internal data class PageTypeOption(
    val label: String,
    val type: LauncherPageType,
)

internal data class PageGridDimensionOption(
    val label: String,
    val dimensions: GridDimensions,
)

internal fun pageOverviewPinActionLabel(
    type: LauncherPageType,
    isPinned: Boolean,
): String? =
    when (type) {
        is LauncherPageType.Generated -> if (isPinned) "Unpin" else "Pin"
        LauncherPageType.AllApps,
        LauncherPageType.Home,
        -> null
    }

internal fun pageGridDimensionOptions(selectedGrid: GridDimensions): List<PageGridDimensionOption> =
    listOf(
        PageGridDimensionOption("${selectedGrid.columns} x ${selectedGrid.rows}", selectedGrid),
        PageGridDimensionOption(
            "${selectedGrid.columns - 1} x ${selectedGrid.rows}",
            selectedGrid.copy(columns = selectedGrid.columns - 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns + 1} x ${selectedGrid.rows}",
            selectedGrid.copy(columns = selectedGrid.columns + 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns} x ${selectedGrid.rows - 1}",
            selectedGrid.copy(rows = selectedGrid.rows - 1),
        ),
        PageGridDimensionOption(
            "${selectedGrid.columns} x ${selectedGrid.rows + 1}",
            selectedGrid.copy(rows = selectedGrid.rows + 1),
        ),
    )
        .filter { option -> option.dimensions.columns >= 1 && option.dimensions.rows >= 1 }
        .distinctBy { option -> option.dimensions }

internal val pageTypeOptions: List<PageTypeOption> =
    listOf(
        PageTypeOption("Classic", LauncherPageType.Home),
        PageTypeOption("All apps", LauncherPageType.AllApps),
        PageTypeOption("Today", LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY)),
        PageTypeOption("Category", LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY)),
        PageTypeOption("App", LauncherPageType.Generated(GeneratedLauncherPageKind.APP)),
        PageTypeOption("Work", LauncherPageType.Generated(GeneratedLauncherPageKind.WORK)),
        PageTypeOption("Personal", LauncherPageType.Generated(GeneratedLauncherPageKind.PERSONAL)),
        PageTypeOption("Favourites", LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES)),
        PageTypeOption("Frequent", LauncherPageType.Generated(GeneratedLauncherPageKind.FREQUENTLY_USED)),
        PageTypeOption("Cards", LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS)),
    )

internal val LauncherPageType.pageOverviewTypeLabel: String
    get() =
        when (this) {
            LauncherPageType.Home -> "Classic"
            LauncherPageType.AllApps -> "All apps"
            is LauncherPageType.Generated -> kind.pageOverviewTypeLabel
        }

private val GeneratedLauncherPageKind.pageOverviewTypeLabel: String
    get() =
        when (this) {
            GeneratedLauncherPageKind.APP -> "App"
            GeneratedLauncherPageKind.CATEGORY -> "Category"
            GeneratedLauncherPageKind.TODAY -> "Today"
            GeneratedLauncherPageKind.WORK -> "Work"
            GeneratedLauncherPageKind.PERSONAL -> "Personal"
            GeneratedLauncherPageKind.FAVOURITES -> "Favourites"
            GeneratedLauncherPageKind.FREQUENTLY_USED -> "Frequent"
            GeneratedLauncherPageKind.NOTIFICATION_CARDS -> "Cards"
        }

internal const val PAGE_OVERVIEW_CARD_WIDTH_DP = 184
internal const val PAGE_OVERVIEW_CARD_SPACING_DP = 8
private const val PAGE_OVERVIEW_CONTENT_PADDING_DP = 16
internal const val PAGE_OVERVIEW_STRIP_TEST_TAG = "page-overview-strip"

internal fun pageOverviewCardTestTag(pageId: String): String = "page-overview-card-$pageId"
