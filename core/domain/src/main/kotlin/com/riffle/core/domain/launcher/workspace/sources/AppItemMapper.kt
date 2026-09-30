package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

/** Maps installed apps and package-level usage to [Item]s. Hidden, excluded and disabled apps are dropped. */
class AppItemMapper(
    private val catalog: InstalledAppCatalog = InstalledAppCatalog(),
) {
    /** Every launchable app in the catalogue's display order, grouped by the app's category when it has one. */
    fun allApps(apps: List<InstalledApp>): List<Item> =
        catalog.visibleApps(apps).map { app -> app.toItem(SourceIds.ALL_APPS, timeEpochMillis = null) }

    /**
     * Usage is package-level, so each package resolves to one visible app (personal profile first), and
     * packages that no longer resolve are skipped. Most recently used first, at most [limit] items.
     */
    fun recentApps(
        usages: List<RecentAppUsage>,
        apps: List<InstalledApp>,
        limit: Int = DEFAULT_RECENT_LIMIT,
    ): List<Item> {
        val byPackage =
            catalog.visibleApps(apps)
                .groupBy { app -> app.identity.packageName }
                .mapValues { (_, candidates) ->
                    candidates.firstOrNull { it.identity.profile.type == AppProfileType.PERSONAL } ?: candidates.first()
                }
        return usages
            .sortedByDescending { usage -> usage.lastUsedAtMillis }
            .distinctBy { usage -> usage.packageName }
            .mapNotNull { usage ->
                byPackage[usage.packageName]?.toItem(SourceIds.RECENT_APPS, usage.lastUsedAtMillis.coerceAtLeast(0L))
            }
            .take(limit.coerceAtLeast(0))
    }

    private fun InstalledApp.toItem(
        sourceId: SourceId,
        timeEpochMillis: Long?,
    ): Item {
        val activity = identity.activityName.value
        return Item(
            id = ItemId("${sourceId.value}:${identity.profile.id.value}:${identity.packageName.value}/$activity"),
            sourceId = sourceId,
            target = ItemTarget.App(identity.packageName.value, identity.profile.id.value),
            title = label,
            icon = ItemImageKeys.appIcon(identity),
            timeEpochMillis = timeEpochMillis,
            groupKey = category,
            groupLabel = category,
            actions = listOf(ItemAction.Open()),
            ext = appProfileExt(identity.profile.type),
        )
    }

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
