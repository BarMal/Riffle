package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId

fun HomeLayout.openedFolder(folderId: LauncherItemId?): FolderItem? =
    folderId?.let { selectedId ->
        (selectedPage.items + dock.items)
            .filterIsInstance<FolderItem>()
            .firstOrNull { folder -> folder.id == selectedId }
    }
