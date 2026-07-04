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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
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
            Text(text = "<")
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
            Text(text = ">")
        }
        TextButton(onClick = { onAction(LauncherShellAction.ExitHomeEditMode) }) {
            Text(text = "Done")
        }
    }
}

@Composable
fun PageOverviewControls(
    layout: HomeLayout,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = remember { mutableStateOf(false) }
    val selectedPage = layout.selectedPage

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            layout.pages.forEachIndexed { index, page ->
                PageOverviewCard(
                    index = index,
                    page = page,
                    isSelected = page.id == layout.selectedPageId,
                    onClick = { onAction(LauncherShellAction.SelectHomePage(page.id)) },
                )
            }
        }
        PageTypeControls(
            selectedType = selectedPage.type,
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
                Text(text = "Add")
            }
            OutlinedButton(onClick = { onAction(LauncherShellAction.DuplicateSelectedHomePage) }) {
                Text(text = "Duplicate")
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
                    ),
                onDismissRequest = { isPageMenuExpanded.value = false },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun PageOverviewCard(
    index: Int,
    page: LauncherPage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .width(PAGE_OVERVIEW_CARD_WIDTH_DP.dp)
                .clip(RoundedCornerShape(PAGE_OVERVIEW_CARD_CORNER_RADIUS_DP.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(PAGE_OVERVIEW_CARD_CORNER_RADIUS_DP.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        border =
            if (isSelected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = pageOverviewLabel(index = index),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = page.type.pageOverviewTypeLabel,
                style = MaterialTheme.typography.labelMedium,
            )
            PageOverviewPreview(page = page)
        }
    }
}

@Composable
private fun PageOverviewPreview(page: LauncherPage) {
    val previewCells = page.previewCells().associateBy { cell -> cell.cell }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PAGE_OVERVIEW_PREVIEW_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP.dp)),
        shape = RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = PAGE_OVERVIEW_PREVIEW_SURFACE_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(PAGE_OVERVIEW_PREVIEW_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP.dp),
        ) {
            repeat(page.grid.rows.coerceAtLeast(1)) { row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP.dp),
                ) {
                    repeat(page.grid.columns.coerceAtLeast(1)) { column ->
                        val kind = previewCells[GridCell(column = column, row = row)]?.kind
                        val cellColor =
                            when (kind) {
                                PagePreviewCellKind.APP -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                PagePreviewCellKind.FOLDER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f)
                                PagePreviewCellKind.WIDGET -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
                                null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
                            }
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                    .clip(RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CELL_CORNER_RADIUS_DP.dp))
                                    .background(cellColor),
                        )
                    }
                }
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
): List<ShortcutContextMenuItem> =
    listOfNotNull(
        ShortcutContextMenuItem(
            label = "Add page",
            action = LauncherShellAction.AddHomePage,
        ),
        overviewMenuItem(includeOverview),
        ShortcutContextMenuItem(
            label = "Duplicate page",
            action = LauncherShellAction.DuplicateSelectedHomePage,
        ),
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

@Composable
fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

private const val PAGE_OVERVIEW_CARD_WIDTH_DP = 148
private const val PAGE_OVERVIEW_CARD_CORNER_RADIUS_DP = 20
private const val PAGE_OVERVIEW_PREVIEW_HEIGHT_DP = 118
private const val PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP = 14
private const val PAGE_OVERVIEW_PREVIEW_PADDING_DP = 8
private const val PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP = 3
private const val PAGE_OVERVIEW_PREVIEW_CELL_CORNER_RADIUS_DP = 3
private const val PAGE_OVERVIEW_PREVIEW_SURFACE_ALPHA = 0.68f
