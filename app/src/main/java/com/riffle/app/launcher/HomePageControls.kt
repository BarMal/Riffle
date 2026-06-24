package com.riffle.app.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
fun PageEditControls(
    pageCount: Int,
    selectedPageIndex: Int,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

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
    }
}

@Composable
fun PageOverviewControls(
    layout: HomeLayout,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isPageMenuExpanded = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                TextButton(
                    onClick = { onAction(LauncherShellAction.SelectHomePage(page.id)) },
                ) {
                    Text(text = pageOverviewLabel(index = index, isSelected = page.id == layout.selectedPageId))
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(LauncherShellAction.EnterHomeEditMode) }) {
                Text(text = "Edit page")
            }
            TextButton(onClick = { isPageMenuExpanded.value = true }) {
                Text(text = "Page menu")
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

private fun pageOverviewLabel(
    index: Int,
    isSelected: Boolean,
): String =
    if (isSelected) {
        "Page ${index + 1} *"
    } else {
        "Page ${index + 1}"
    }
