package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutSet

internal fun LauncherBackupDocument.isImportableBackup(): Boolean = homeLayoutSet.isImportableBackupLayoutSet()

private fun HomeLayoutSet.isImportableBackupLayoutSet(): Boolean =
    layouts.isNotEmpty() &&
        activeKey in layouts &&
        layouts.values.all(HomeLayout::isImportableBackupLayout)

private fun HomeLayout.isImportableBackupLayout(): Boolean =
    pages.isNotEmpty() &&
        pages.any { page -> page.id == selectedPageId } &&
        pages.all { page -> page.grid.columns > 0 && page.grid.rows > 0 }
