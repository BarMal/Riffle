package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppProfileSelection
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.filterByProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey

data class GeneratedLauncherPageContentPlanInput(
    val installedApps: List<InstalledApp> = emptyList(),
    val notificationGroupKeys: List<AppNotificationGroupKey> = emptyList(),
    val appCategoriesAvailable: Boolean = false,
    val favouriteAppsAvailable: Boolean = false,
    val usageStatsAvailable: Boolean = false,
    val notificationCardsAvailable: Boolean = notificationGroupKeys.isNotEmpty(),
) {
    internal val generatedPageData: GeneratedLauncherPageData
        get() =
            GeneratedLauncherPageData(
                installedApps = installedApps,
                appCategoriesAvailable = appCategoriesAvailable,
                favouriteAppsAvailable = favouriteAppsAvailable,
                usageStatsAvailable = usageStatsAvailable,
                notificationCardsAvailable =
                    notificationCardsAvailable || notificationGroupKeys.isNotEmpty(),
            )
}

data class GeneratedLauncherPageContentPlan(
    val pageId: LauncherPageId,
    val kind: GeneratedLauncherPageKind,
    val canCreate: Boolean,
    val requiredDataSources: Set<GeneratedLauncherPageDataSource>,
    val items: List<GeneratedLauncherPageContentItem> = emptyList(),
)

sealed interface GeneratedLauncherPageContentItem {
    val stableId: GeneratedLauncherPageContentItemId

    data class App(
        val identity: AppIdentity,
        override val stableId: GeneratedLauncherPageContentItemId = identity.generatedContentItemId,
    ) : GeneratedLauncherPageContentItem

    data class NotificationGroup(
        val key: AppNotificationGroupKey,
        override val stableId: GeneratedLauncherPageContentItemId = key.generatedContentItemId,
    ) : GeneratedLauncherPageContentItem
}

@JvmInline
value class GeneratedLauncherPageContentItemId(val value: String)

private typealias NotificationGroupContentItem = GeneratedLauncherPageContentItem.NotificationGroup

class GeneratedLauncherPageContentPlanner {
    fun plan(
        kind: GeneratedLauncherPageKind,
        input: GeneratedLauncherPageContentPlanInput = GeneratedLauncherPageContentPlanInput(),
        pageId: LauncherPageId = kind.spec.defaultPageId(),
    ): GeneratedLauncherPageContentPlan {
        val spec = kind.spec
        val canCreate = spec.canCreateWith(input.generatedPageData)

        return GeneratedLauncherPageContentPlan(
            pageId = pageId,
            kind = kind,
            canCreate = canCreate,
            requiredDataSources = spec.requiredDataSources,
            items =
                if (canCreate) {
                    input.contentItemsFor(kind)
                } else {
                    emptyList()
                },
        )
    }

    fun plansFor(
        kinds: List<GeneratedLauncherPageKind>,
        input: GeneratedLauncherPageContentPlanInput = GeneratedLauncherPageContentPlanInput(),
    ): List<GeneratedLauncherPageContentPlan> = kinds.map { kind -> plan(kind = kind, input = input) }
}

private fun GeneratedLauncherPageContentPlanInput.contentItemsFor(
    kind: GeneratedLauncherPageKind,
): List<GeneratedLauncherPageContentItem> =
    when (kind) {
        GeneratedLauncherPageKind.APP,
        GeneratedLauncherPageKind.TODAY,
        -> visibleAppItems()

        GeneratedLauncherPageKind.WORK -> visibleAppItems(profileSelection = AppProfileSelection.work())
        GeneratedLauncherPageKind.PERSONAL -> visibleAppItems(profileSelection = AppProfileSelection.personal())
        GeneratedLauncherPageKind.NOTIFICATION_CARDS -> notificationGroupItems()

        GeneratedLauncherPageKind.CATEGORY -> categoryAppItems()

        GeneratedLauncherPageKind.FAVOURITES,
        GeneratedLauncherPageKind.FREQUENTLY_USED,
        -> emptyList()
    }

private fun GeneratedLauncherPageContentPlanInput.visibleAppItems(
    profileSelection: AppProfileSelection = AppProfileSelection.all(),
): List<GeneratedLauncherPageContentItem.App> =
    generatedPageData.visibleInstalledApps
        .asSequence()
        .filterByProfile(profileSelection)
        .sortedWith(installedAppContentOrder)
        .distinctBy { app -> app.identity }
        .map { app -> GeneratedLauncherPageContentItem.App(identity = app.identity) }
        .toList()

private fun GeneratedLauncherPageContentPlanInput.notificationGroupItems(): List<NotificationGroupContentItem> =
    notificationGroupKeys
        .asSequence()
        .sortedWith(notificationGroupContentOrder)
        .distinct()
        .map { key -> NotificationGroupContentItem(key = key) }
        .toList()

private fun GeneratedLauncherPageContentPlanInput.categoryAppItems(): List<GeneratedLauncherPageContentItem.App> =
    generatedPageData.visibleInstalledApps
        .asSequence()
        .filter { app -> !app.category.isNullOrBlank() }
        .sortedWith(
            compareBy<InstalledApp> { app -> app.category.orEmpty().lowercase() }
                .thenBy { app -> app.label.lowercase() }
                .then(installedAppContentOrder),
        ).distinctBy { app -> app.identity }
        .map { app -> GeneratedLauncherPageContentItem.App(identity = app.identity) }
        .toList()

private val installedAppContentOrder: Comparator<InstalledApp> =
    compareBy<InstalledApp> { app -> app.identity.packageName.value }
        .thenBy { app -> app.identity.activityName.value }
        .thenBy { app -> app.identity.profile.type.ordinal }
        .thenBy { app -> app.identity.profile.id.value }

private val notificationGroupContentOrder: Comparator<AppNotificationGroupKey> =
    compareBy<AppNotificationGroupKey> { key -> key.packageName.value }
        .thenBy { key -> key.profileId.value }

private val AppIdentity.generatedContentItemId: GeneratedLauncherPageContentItemId
    get() =
        GeneratedLauncherPageContentItemId(
            "app:${profile.id.value}:${profile.type.name}:${packageName.value}:${activityName.value}",
        )

private val AppNotificationGroupKey.generatedContentItemId: GeneratedLauncherPageContentItemId
    get() = GeneratedLauncherPageContentItemId("notification:${profileId.value}:${packageName.value}")
