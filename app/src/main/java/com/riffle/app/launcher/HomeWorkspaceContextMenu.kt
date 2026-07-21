package com.riffle.app.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset

@Composable
internal fun HomeBackgroundContextMenu(
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isMenuExpanded = remember { mutableStateOf(false) }
    val menuOffset = remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    val showMenu = { offset: DpOffset ->
        haptics.longPress()
        menuOffset.value = offset
        isMenuExpanded.value = true
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(onClick, density) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { pressOffset ->
                            showMenu(
                                DpOffset(
                                    x = with(density) { pressOffset.x.toDp() },
                                    y = with(density) { pressOffset.y.toDp() },
                                ),
                            )
                        },
                    )
                }
                .semantics {
                    onClick(action = {
                        onClick()
                        true
                    })
                    onLongClick(
                        label = "Show home actions",
                        action = {
                            showMenu(DpOffset.Zero)
                            true
                        },
                    )
                },
    ) {
        ShortcutContextMenu(
            expanded = isMenuExpanded.value,
            items = homeWorkspaceContextMenuItems(),
            onDismissRequest = {
                isMenuExpanded.value = false
                menuOffset.value = DpOffset.Zero
            },
            onAction = onAction,
            offset = menuOffset.value,
        )
    }
}

internal fun homeWorkspaceContextMenuItems(): List<ShortcutContextMenuItem> =
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
            label = "Settings",
            action = LauncherShellAction.OpenSettings,
        ),
        ShortcutContextMenuItem(
            label = "Edit page",
            action = LauncherShellAction.EnterHomeEditMode,
        ),
        ShortcutContextMenuItem(
            label = "Manage pages",
            action = LauncherShellAction.EnterHomePageOverview,
        ),
    )
