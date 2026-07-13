package com.riffle.app.launcher

internal data class HomeItemPressHandlers(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
)

internal fun homeShortcutPressHandlers(
    isEditing: Boolean,
    onShowContextMenu: () -> Unit,
    onLaunch: () -> Unit,
): HomeItemPressHandlers =
    HomeItemPressHandlers(
        onTap = if (isEditing) onShowContextMenu else onLaunch,
        onLongPress = onShowContextMenu,
    )

internal fun homeFolderPressHandlers(
    isEditing: Boolean,
    onShowContextMenu: () -> Unit,
    onOpenFolder: () -> Unit,
): HomeItemPressHandlers =
    HomeItemPressHandlers(
        onTap = if (isEditing) onShowContextMenu else onOpenFolder,
        onLongPress = onShowContextMenu,
    )
