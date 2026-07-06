package com.riffle.core.domain.launcher.home

class GeneratedLauncherPageContentItemMaterializer {
    fun materialize(
        plan: GeneratedLauncherPageContentPlan,
        appLabelProvider: (GeneratedLauncherPageContentItem.App) -> String,
    ): GeneratedLauncherPageContentItemMaterializationResult =
        materialize(
            items = plan.items,
            appLabelProvider = appLabelProvider,
        )

    fun materialize(
        items: List<GeneratedLauncherPageContentItem>,
        appLabelProvider: (GeneratedLauncherPageContentItem.App) -> String,
    ): GeneratedLauncherPageContentItemMaterializationResult {
        val seenStableIds = mutableSetOf<GeneratedLauncherPageContentItemId>()
        val appShortcuts = mutableListOf<AppShortcutItem>()
        val skippedItems = mutableListOf<GeneratedLauncherPageContentItemMaterializationSkip>()

        items.forEach { item ->
            if (!seenStableIds.add(item.stableId)) {
                skippedItems +=
                    GeneratedLauncherPageContentItemMaterializationSkip(
                        stableId = item.stableId,
                        reason = GeneratedLauncherPageContentItemMaterializationSkipReason.DUPLICATE_STABLE_ID,
                    )
                return@forEach
            }

            when (item) {
                is GeneratedLauncherPageContentItem.App ->
                    appShortcuts +=
                        AppShortcutItem(
                            id = item.generatedPageLauncherItemId,
                            appIdentity = item.identity,
                            label = appLabelProvider(item),
                        )

                // Notification groups will materialize as card-backed content in a later slice.
                is GeneratedLauncherPageContentItem.NotificationGroup ->
                    skippedItems +=
                        GeneratedLauncherPageContentItemMaterializationSkip(
                            stableId = item.stableId,
                            reason =
                                GeneratedLauncherPageContentItemMaterializationSkipReason
                                    .UNSUPPORTED_NOTIFICATION_GROUP,
                        )
            }
        }

        return GeneratedLauncherPageContentItemMaterializationResult(
            items = appShortcuts,
            skippedItems = skippedItems,
        )
    }
}

data class GeneratedLauncherPageContentItemMaterializationResult(
    val items: List<AppShortcutItem>,
    val skippedItems: List<GeneratedLauncherPageContentItemMaterializationSkip> = emptyList(),
)

data class GeneratedLauncherPageContentItemMaterializationSkip(
    val stableId: GeneratedLauncherPageContentItemId,
    val reason: GeneratedLauncherPageContentItemMaterializationSkipReason,
)

enum class GeneratedLauncherPageContentItemMaterializationSkipReason {
    DUPLICATE_STABLE_ID,
    UNSUPPORTED_NOTIFICATION_GROUP,
}

private val GeneratedLauncherPageContentItem.generatedPageLauncherItemId: LauncherItemId
    get() = LauncherItemId("generated-page:${stableId.value}")
