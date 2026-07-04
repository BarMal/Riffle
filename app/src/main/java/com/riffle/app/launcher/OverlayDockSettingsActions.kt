package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP
import com.riffle.core.domain.launcher.settings.MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.coerceOverlayDockVerticalOffset

internal fun OverlayDockSettings.withOverlayDockSettingsAction(action: LauncherShellAction): OverlayDockSettings =
    withOverlayDockConfigurationAction(action)
        ?: withOverlayDockItemsAction(action)
        ?: this

private fun OverlayDockSettings.withOverlayDockConfigurationAction(action: LauncherShellAction): OverlayDockSettings? =
    when (action) {
        is LauncherShellAction.SelectOverlayDockEnabled -> copy(enabled = action.enabled)
        is LauncherShellAction.SelectOverlayDockEdge -> copy(edge = action.edge)
        is LauncherShellAction.SelectOverlayDockHandleThickness ->
            copy(
                handleThicknessDp =
                    action.thicknessDp.coerceIn(
                        MIN_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                        MAX_OVERLAY_DOCK_HANDLE_THICKNESS_DP,
                    ),
            )

        is LauncherShellAction.SelectOverlayDockHandleHeight ->
            copy(
                handleHeightDp =
                    action.heightDp.coerceIn(
                        MIN_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                        MAX_OVERLAY_DOCK_HANDLE_HEIGHT_DP,
                    ),
            )

        is LauncherShellAction.SelectOverlayDockVerticalOffset ->
            copy(verticalOffsetDp = action.offsetDp.coerceOverlayDockVerticalOffset())

        is LauncherShellAction.SelectOverlayDockHandleAlpha ->
            copy(
                handleAlphaPercent =
                    action.alphaPercent.coerceIn(
                        MIN_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                        MAX_OVERLAY_DOCK_HANDLE_ALPHA_PERCENT,
                    ),
            )

        is LauncherShellAction.SelectOverlayDockExpandedIconSize ->
            copy(
                expandedIconSizeDp =
                    action.sizeDp.coerceIn(
                        MIN_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                        MAX_OVERLAY_DOCK_EXPANDED_ICON_SIZE_DP,
                    ),
            )

        is LauncherShellAction.SelectOverlayDockExpandedOrientation -> copy(expandedOrientation = action.orientation)
        is LauncherShellAction.SelectOverlayDockShowLabels -> copy(showLabels = action.showLabels)
        else -> null
    }

private fun OverlayDockSettings.withOverlayDockItemsAction(action: LauncherShellAction): OverlayDockSettings? =
    when (action) {
        is LauncherShellAction.AddAppToFloatingDock -> withAddedFloatingDockApp(action.app)
        is LauncherShellAction.AddAppShortcutToFloatingDock -> withAddedFloatingDockAppShortcut(action.shortcut)
        is LauncherShellAction.RemoveFloatingDockShortcut ->
            copy(items = items.filterNot { item -> item.id == action.itemId })

        is LauncherShellAction.MoveFloatingDockShortcut ->
            copy(items = items.moveFloatingDockItem(itemId = action.itemId, indexDelta = action.direction.indexDelta))

        else -> null
    }

private fun OverlayDockSettings.withAddedFloatingDockApp(app: InstalledApp): OverlayDockSettings =
    when {
        items.any { item -> item.appIdentity == app.identity && item.appShortcutId == null } -> copy(enabled = true)
        else -> copy(enabled = true, items = items + app.floatingDockShortcut(existingShortcuts = items))
    }

private fun OverlayDockSettings.withAddedFloatingDockAppShortcut(shortcut: AppShortcut): OverlayDockSettings =
    when {
        items.any { item -> item.appIdentity == shortcut.appIdentity && item.appShortcutId == shortcut.id } ->
            copy(enabled = true)

        else -> copy(enabled = true, items = items + shortcut.floatingDockShortcut(existingShortcuts = items))
    }

private fun InstalledApp.floatingDockShortcut(existingShortcuts: List<AppShortcutItem>): AppShortcutItem =
    AppShortcutItem(
        id =
            LauncherItemId(
                "floating-dock:${identity.shortcutKey}:${nextFloatingDockShortcutOrdinal(existingShortcuts)}",
            ),
        appIdentity = identity,
        label = label,
    )

private fun AppShortcut.floatingDockShortcut(existingShortcuts: List<AppShortcutItem>): AppShortcutItem {
    val shortcutOrdinal = nextFloatingDockShortcutOrdinal(existingShortcuts)

    return AppShortcutItem(
        id = LauncherItemId("floating-dock:${appIdentity.shortcutKey}:${id.value}:$shortcutOrdinal"),
        appIdentity = appIdentity,
        label = longLabel ?: shortLabel,
        appShortcutId = id,
    )
}

private fun InstalledApp.nextFloatingDockShortcutOrdinal(existingShortcuts: List<AppShortcutItem>): Int =
    existingShortcuts.count { item -> item.appIdentity == identity } + 1

private fun AppShortcut.nextFloatingDockShortcutOrdinal(existingShortcuts: List<AppShortcutItem>): Int =
    existingShortcuts.count { item -> item.appIdentity == appIdentity && item.appShortcutId == id } + 1

private val AppIdentity.shortcutKey: String
    get() = "${profile.id.value}:${packageName.value}/${activityName.value}"

private fun List<AppShortcutItem>.moveFloatingDockItem(
    itemId: LauncherItemId,
    indexDelta: Int,
): List<AppShortcutItem> {
    val currentIndex = indexOfFirst { item -> item.id == itemId }
    val targetIndex = currentIndex + indexDelta

    if (currentIndex !in indices || targetIndex !in indices) {
        return this
    }

    return toMutableList()
        .apply { add(targetIndex, removeAt(currentIndex)) }
        .toList()
}
