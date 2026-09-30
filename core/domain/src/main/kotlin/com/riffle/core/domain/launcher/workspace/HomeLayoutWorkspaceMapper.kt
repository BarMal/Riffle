package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode

/**
 * Maps one stored per-mode [HomeLayout] onto a [Workspace] (mapping table: docs/product/workspaces-sources-lenses.md).
 *
 * The mapping is pure and deterministic (ids derive from the device class, mode and page ids), so
 * migrating twice gives the same result. It never reads item content: placed home items stay in
 * `HomeLayout`, and a migrated home page references its page there through [WorkspaceSourceIds.HOME_GRID].
 */
internal object HomeLayoutWorkspaceMapper {
    const val FINDER_PAGE_ID = "finder"
    const val NOTIFICATIONS_PAGE_ID = "notifications"

    fun workspaceId(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ) = WorkspaceId("ws:${deviceClass.name.lowercase()}:${mode.name.lowercase()}")

    fun map(
        deviceClass: HomeLayoutDeviceClass,
        layout: HomeLayout,
        mode: LauncherViewMode,
    ): Workspace {
        val pages = uniquePages(layout.pages)
        val allPages =
            when (mode) {
                LauncherViewMode.STANDARD_APP_DRAWER -> pages
                LauncherViewMode.HOME_SCREEN_LIBRARY -> pages + finderPage()
                LauncherViewMode.CARD_INTERFACE -> listOf(notificationsPageSet()) + pages
            }
        return Workspace(
            id = workspaceId(deviceClass, mode),
            name = modeName(mode),
            pages = allPages,
            dock = WorkspaceDock(dynamicSection = dockDynamicSection(layout)),
        )
    }

    fun modeName(mode: LauncherViewMode): String =
        when (mode) {
            LauncherViewMode.STANDARD_APP_DRAWER -> "Standard"
            LauncherViewMode.HOME_SCREEN_LIBRARY -> "Library"
            LauncherViewMode.CARD_INTERFACE -> "Cards"
        }

    /** The dock's notification strip becomes its dynamic section: newest notifications as icons, one per slot. */
    private fun dockDynamicSection(layout: HomeLayout): LensBinding? =
        layout.dock.takeIf { it.showNotificationCards }?.let { dock ->
            LensBinding(
                lens =
                    Lens(
                        sources = listOf(SourceIds.NOTIFICATIONS),
                        sort = LensSort(LensSortField.TIME, SortDirection.DESCENDING),
                        limit = dock.notificationSlotCount.coerceAtLeast(1),
                    ),
                expression = ExpressionKind.ICON_ROW,
            )
        }

    private fun uniquePages(pages: List<LauncherPage>): List<PageHost> {
        val seen = HashSet<String>()
        return pages.mapIndexed { index, page ->
            var id = "page:${page.id.value.ifBlank { index.toString() }}"
            while (!seen.add(id)) id += "#"
            boundPage(ContainerId(id), page.type, page.id.value)
        }
    }

    private fun boundPage(
        id: ContainerId,
        type: LauncherPageType,
        pageId: String,
    ): PageHost =
        when (type) {
            LauncherPageType.Home ->
                page(id, homeGridLens(pageId), ExpressionKind.ICON_GRID)
            LauncherPageType.AllApps ->
                page(id, appsLens(), ExpressionKind.ALPHA_LIST)
            is LauncherPageType.Generated -> generatedPage(id, type.kind)
        }

    private fun generatedPage(
        id: ContainerId,
        kind: GeneratedLauncherPageKind,
    ): PageHost =
        when (kind) {
            GeneratedLauncherPageKind.APP -> page(id, appsLens(), ExpressionKind.ICON_GRID)
            GeneratedLauncherPageKind.CATEGORY ->
                page(id, appsByGroup(), ExpressionKind.CATEGORIES)
            GeneratedLauncherPageKind.TODAY ->
                page(id, recentAppsLens(), ExpressionKind.LIST)
            GeneratedLauncherPageKind.WORK ->
                page(
                    id,
                    profileLens(WorkspaceSourceIds.PROFILE_WORK),
                    ExpressionKind.ICON_GRID,
                )
            GeneratedLauncherPageKind.PERSONAL ->
                page(id, profileLens(WorkspaceSourceIds.PROFILE_PERSONAL), ExpressionKind.ICON_GRID)
            GeneratedLauncherPageKind.FAVOURITES ->
                page(id, favouriteAppsLens(), ExpressionKind.ICON_GRID)
            GeneratedLauncherPageKind.FREQUENTLY_USED ->
                page(id, frequentAppsLens(), ExpressionKind.ICON_GRID)
            GeneratedLauncherPageKind.NOTIFICATION_CARDS ->
                page(id, notificationsLens(), ExpressionKind.CARD_STACK)
        }

    private fun finderPage(): PageHost =
        PageContainer(
            id = ContainerId(FINDER_PAGE_ID),
            content = PageContent.Bound(LensBinding(appsByGroup(), ExpressionKind.CATEGORIES)),
            role = PageRole.FINDER,
        )

    /**
     * Cards mode centres on notifications grouped by app: one card stack per app, as a page-set.
     */
    private fun notificationsPageSet(): PageHost =
        PageSetContainer(
            id = ContainerId(NOTIFICATIONS_PAGE_ID),
            binding =
                LensBinding(
                    notificationsLens().copy(group = LensGroup.ByGroupKey),
                    ExpressionKind.CARD_STACK,
                ),
        )

    private fun page(
        id: ContainerId,
        lens: Lens,
        expression: ExpressionKind,
    ): PageHost = PageContainer(id, PageContent.Bound(LensBinding(lens, expression)))
}
