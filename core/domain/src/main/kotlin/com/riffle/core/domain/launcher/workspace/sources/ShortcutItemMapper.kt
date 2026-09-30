package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/** Maps app shortcuts (quick actions) to [Item]s grouped by their app. Disabled shortcuts are dropped. */
class ShortcutItemMapper(
    private val catalog: InstalledAppCatalog = InstalledAppCatalog(),
) {
    fun quickActions(
        apps: List<InstalledApp>,
        shortcutsByApp: AppShortcutsByApp,
    ): List<Item> =
        catalog.visibleApps(apps).flatMap { app ->
            shortcutsByApp[app.identity].orEmpty()
                .filter(AppShortcut::enabled)
                .map { shortcut -> shortcut.toItem(app) }
        }

    private fun AppShortcut.toItem(app: InstalledApp): Item {
        val identity = app.identity
        val profile = identity.profile.id.value
        val activity = identity.activityName.value
        return Item(
            id =
                ItemId(
                    "${SourceIds.QUICK_ACTIONS.value}:$profile:${identity.packageName.value}:$activity:${id.value}",
                ),
            sourceId = SourceIds.QUICK_ACTIONS,
            target = ItemTarget.Shortcut(identity.packageName.value, id.value, profile),
            title = shortLabel,
            subtitle = app.label,
            body = longLabel?.takeIf { label -> label.isNotBlank() && label != shortLabel },
            icon = ItemImageKeys.appIcon(identity),
            groupKey = "${identity.packageName.value}:$profile",
            groupLabel = app.label,
            actions = listOf(ItemAction.Open()),
            ext = appProfileExt(identity.profile.type),
        )
    }
}
