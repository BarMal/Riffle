package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.MAX_HOME_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal fun LauncherBackupDocument.isImportableBackup(): Boolean =
    homeLayoutSet.isImportableBackupLayoutSet() &&
        launcherSettings.isImportableBackupSettings()

private fun HomeLayoutSet.isImportableBackupLayoutSet(): Boolean =
    layouts.isNotEmpty() &&
        activeKey in layouts &&
        layouts.values.all(HomeLayout::isImportableBackupLayout)

private fun HomeLayout.isImportableBackupLayout(): Boolean =
    pages.isNotEmpty() &&
        pages.any { page -> page.id == selectedPageId } &&
        pages.all { page -> page.grid.columns > 0 && page.grid.rows > 0 } &&
        settings.labels.iconSizeDp in MIN_HOME_ICON_SIZE_DP..MAX_HOME_ICON_SIZE_DP

private fun LauncherSettings.isImportableBackupSettings(): Boolean = overlayDock.isImportableBackupOverlayDockSettings()

private fun OverlayDockSettings.isImportableBackupOverlayDockSettings(): Boolean =
    handleThicknessDp in MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP..MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP &&
        handleHeightDp in MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP..MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP &&
        verticalOffsetDp in MIN_OVERLAY_DOCK_VERTICAL_OFFSET_DP..MAX_OVERLAY_DOCK_VERTICAL_OFFSET_DP &&
        handleAlphaPercent in MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT..MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT &&
        expandedIconSizeDp in MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP..MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
