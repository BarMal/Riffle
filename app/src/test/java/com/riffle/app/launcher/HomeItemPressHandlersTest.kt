package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeItemPressHandlersTest {
    @Test
    fun shortcutTapLaunchesAndLongPressShowsContextMenu() {
        val events = mutableListOf<String>()
        val handlers =
            homeShortcutPressHandlers(
                isEditing = false,
                onShowContextMenu = { events += "context menu" },
                onLaunch = { events += "launch" },
            )

        handlers.onTap()
        handlers.onLongPress()

        assertEquals(listOf("launch", "context menu"), events)
    }

    @Test
    fun folderTapOpensAndLongPressShowsContextMenu() {
        val events = mutableListOf<String>()
        val handlers =
            homeFolderPressHandlers(
                isEditing = false,
                onShowContextMenu = { events += "context menu" },
                onOpenFolder = { events += "open folder" },
            )

        handlers.onTap()
        handlers.onLongPress()

        assertEquals(listOf("open folder", "context menu"), events)
    }

    @Test
    fun editingTapShowsContextMenuForShortcutsAndFolders() {
        val events = mutableListOf<String>()

        homeShortcutPressHandlers(
            isEditing = true,
            onShowContextMenu = { events += "shortcut context menu" },
            onLaunch = { events += "launch" },
        ).onTap()
        homeFolderPressHandlers(
            isEditing = true,
            onShowContextMenu = { events += "folder context menu" },
            onOpenFolder = { events += "open folder" },
        ).onTap()

        assertEquals(listOf("shortcut context menu", "folder context menu"), events)
    }
}
