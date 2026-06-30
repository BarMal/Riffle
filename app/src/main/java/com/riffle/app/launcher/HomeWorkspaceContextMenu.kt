package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeEmptyCellContextMenu(
    pageCount: Int,
    selectedPageIndex: Int,
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMenuExpanded = remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { Unit },
                    onLongClick = {
                        haptics.longPress()
                        isMenuExpanded.value = true
                    },
                    onLongClickLabel = "Show home actions",
                ),
    ) {
        ShortcutContextMenu(
            expanded = isMenuExpanded.value,
            items =
                homeWorkspaceContextMenuItems(
                    pageCount = pageCount,
                    selectedPageIndex = selectedPageIndex,
                ),
            onDismissRequest = { isMenuExpanded.value = false },
            onAction = onAction,
        )
    }
}

internal fun homeWorkspaceContextMenuItems(
    pageCount: Int,
    selectedPageIndex: Int,
): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = "Create folder",
            action = LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
        ),
        ShortcutContextMenuItem(
            label = "Widgets",
            action = LauncherShellAction.OpenWidgetPicker,
        ),
        ShortcutContextMenuItem(
            label = "Previous page",
            action = LauncherShellAction.SelectPreviousHomePage,
            enabled = selectedPageIndex > 0,
        ),
        ShortcutContextMenuItem(
            label = "Next page",
            action = LauncherShellAction.SelectNextHomePage,
            enabled = selectedPageIndex < pageCount - 1,
        ),
    ) +
        pageManagementMenuItems(
            pageCount = pageCount,
            selectedPageIndex = selectedPageIndex,
            includeOverview = false,
        )
