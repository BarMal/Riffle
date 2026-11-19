package com.riffle.app.launcher

internal data class HomeItemPressHandlers(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
)

internal fun homeShortcutPressHandlers(
    isEditing: Boolean,
    onShowContextMenu: () -> Unit,
    onLaunch: () -> Unit,
    /** While editing, a tap ticks or unticks the item (multi-select); without it the tap shows the menu. */
    onToggleSelection: (() -> Unit)? = null,
): HomeItemPressHandlers =
    HomeItemPressHandlers(
        onTap = if (isEditing) (onToggleSelection ?: onShowContextMenu) else onLaunch,
        onLongPress = onShowContextMenu,
    )

internal fun homeFolderPressHandlers(
    isEditing: Boolean,
    onShowContextMenu: () -> Unit,
    onOpenFolder: () -> Unit,
    onToggleSelection: (() -> Unit)? = null,
): HomeItemPressHandlers =
    HomeItemPressHandlers(
        onTap = if (isEditing) (onToggleSelection ?: onShowContextMenu) else onOpenFolder,
        onLongPress = onShowContextMenu,
    )
