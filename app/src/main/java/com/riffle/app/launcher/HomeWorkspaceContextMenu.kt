package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R

/**
 * What a tap on Home's empty room (the background under and between the apps, folders and widgets) does besides its own
 * `onClick`: with the Stream standing beside Home it closes the Stream. Null leaves the tap as it was.
 */
internal val LocalHomeEmptyTap = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Home's empty-space handler (the existing long-press menu and tap) as a node that fills its parent. It is the layer
 * under a surface: what is on top of it (an item, the Dock, the handle) takes its own presses, and a press on nothing
 * reaches this and opens the menu where the finger is. Hand it a [state] that something else also opens (the handle's
 * long-press) to share the one menu.
 */
@Composable
internal fun HomeBackgroundContextMenu(
    haptics: LauncherHaptics,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
    items: List<ShortcutContextMenuItem> = homeWorkspaceContextMenuItems(rememberSettingsText()),
    longClickLabel: String = stringResource(R.string.menu_show_home_actions),
    onClick: () -> Unit = {},
    state: PressMenuState = remember { PressMenuState() },
) {
    val emptyTap = LocalHomeEmptyTap.current
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pressMenu(
                    state = state,
                    haptics = haptics,
                    longClickLabel = longClickLabel,
                    onTap = {
                        onClick()
                        emptyTap?.invoke()
                    },
                ),
    ) {
        PressMenuAnchor(state) {
            ShortcutContextMenu(
                expanded = state.expanded,
                items = items,
                onDismissRequest = state::dismiss,
                onAction = onAction,
            )
        }
    }
}

/**
 * What long-pressing the empty space of a grid offers, which depends on whose grid it is.
 *
 * The panel is a short grid on the dock's shelf, not a home page: page management and folders do
 * not apply to it, and adding a widget has to say so, because the picker covers the shelf and the
 * widget cannot simply be dragged back onto it.
 */
internal fun backgroundContextMenuItems(
    surface: ShortcutContextSurface,
    text: SettingsText,
): List<ShortcutContextMenuItem> =
    when (surface) {
        ShortcutContextSurface.DOCK_PANEL ->
            listOf(
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_add_widget),
                    action = LauncherShellAction.OpenWidgetPickerForDockPanel,
                ),
            )

        ShortcutContextSurface.HOME, ShortcutContextSurface.DOCK -> homeWorkspaceContextMenuItems(text)
    }

@Composable
internal fun ShortcutContextSurface.backgroundLongClickLabel(): String =
    when (this) {
        ShortcutContextSurface.DOCK_PANEL -> stringResource(R.string.menu_show_panel_actions)
        ShortcutContextSurface.HOME, ShortcutContextSurface.DOCK -> stringResource(R.string.menu_show_home_actions)
    }

internal fun homeWorkspaceContextMenuItems(text: SettingsText): List<ShortcutContextMenuItem> =
    listOf(
        ShortcutContextMenuItem(
            label = text.text(R.string.menu_create_folder),
            action = LauncherShellAction.CreateEmptyHomeFolder(label = text.text(R.string.default_folder_name)),
        ),
        ShortcutContextMenuItem(
            label = text.text(R.string.menu_widgets),
            action = LauncherShellAction.OpenWidgetPicker,
        ),
        ShortcutContextMenuItem(
            label = text.text(R.string.menu_settings),
            action = LauncherShellAction.OpenSettings,
        ),
        ShortcutContextMenuItem(
            label = text.text(R.string.menu_edit_page),
            action = LauncherShellAction.EnterHomeEditMode,
        ),
        ShortcutContextMenuItem(
            label = text.text(R.string.menu_manage_pages),
            action = LauncherShellAction.EnterHomePageOverview,
        ),
    )
