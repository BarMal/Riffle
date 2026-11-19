package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.pool.ArrangementAdapter
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.Placement
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolFolder
import com.riffle.core.domain.launcher.workspace.pool.PoolItem
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolStackMember
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget

/**
 * Takes a snapshot of Home's pages ([LauncherPage]s of the Home type; generated pages and the Dock are not Home's
 * arrangement). Pure; never allocates or releases a widget host id: a platform widget is described by the provider
 * [providerOf] names for its host id, and the snapshot holds no host id at all.
 */
object SavedHomeCapture {
    fun capture(
        pages: List<LauncherPage>,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
        id: String,
        name: String,
        kind: SavedHomeLayoutKind,
        savedAtEpochMillis: Long,
    ): SavedHomeLayout {
        val items = LinkedHashMap<PoolItemId, PoolItem>()
        val captured =
            pages.filter { it.type == LauncherPageType.Home }.map { page ->
                val seen = HashSet<PoolItemId>()
                ArrangementPage(
                    id = page.id,
                    grid = page.grid,
                    placements =
                        page.items.mapNotNull { item ->
                            val at = item.placement
                            val itemId = item.id.value.takeIf { it.isNotBlank() }?.let(::PoolItemId)
                            if (at == null || itemId == null || !seen.add(itemId)) {
                                null
                            } else {
                                items.getOrPut(itemId) { describe(itemId, item, providerOf) }
                                Placement(itemId, at)
                            }
                        },
                    isPinned = page.isPinned,
                )
            }
        return SavedHomeLayout(id, name, kind, savedAtEpochMillis, captured, items)
    }

    private fun describe(
        id: PoolItemId,
        item: LauncherItem,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    ): PoolItem =
        when (item) {
            is AppShortcutItem -> PoolApp(id, item.appIdentity, item.label, item.appShortcutId)
            is FolderItem -> PoolFolder(id, item.label, ArrangementAdapter.entriesOf(item))
            is WidgetItem -> describeWidget(id, item, providerOf)
        }

    private fun describeWidget(
        id: PoolItemId,
        item: WidgetItem,
        providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    ): PoolWidget {
        val members = item.members.map { it.describe(providerOf) }
        val lead = members.first()
        return PoolWidget(
            id = id,
            label = lead.label,
            resizeConstraints = item.resizeConstraints,
            provider = lead.provider,
            hostedId = null,
            stack = if (item.isStack) members else emptyList(),
            shown = if (item.isStack) item.shownIndex else 0,
        )
    }

    private fun StackedWidget.describe(providerOf: (HostedWidgetId) -> WidgetProviderIdentity?): PoolStackMember =
        PoolStackMember(
            label = label,
            resizeConstraints = resizeConstraints,
            provider =
                glance?.let(GlanceWidgets::identity)
                    ?: appWidgetId.takeIf { it != ArrangementAdapter.UNBOUND_HOST_ID }?.let(providerOf),
            hostedId = null,
        )
}
