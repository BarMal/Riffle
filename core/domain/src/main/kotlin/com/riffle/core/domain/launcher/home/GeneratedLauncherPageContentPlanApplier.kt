package com.riffle.core.domain.launcher.home

class GeneratedLauncherPageContentPlanApplier {
    fun apply(
        plan: GeneratedLauncherPageContentPlan,
        page: LauncherPage,
        appLabelProvider: (GeneratedLauncherPageContentItem.App) -> String,
    ): GeneratedLauncherPageContentPlanApplyResult {
        val pageType = page.type
        val rejectionReason =
            when {
                !plan.canCreate -> GeneratedLauncherPageContentPlanApplyRejectionReason.UNAVAILABLE_PLAN
                pageType !is LauncherPageType.Generated ->
                    GeneratedLauncherPageContentPlanApplyRejectionReason.NON_GENERATED_PAGE
                pageType.kind != plan.kind -> GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_KIND_MISMATCH
                page.items.any { item ->
                    !item.id.value.startsWith(GENERATED_PAGE_LAUNCHER_ITEM_ID_PREFIX)
                } ->
                    GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_HAS_MANUAL_ITEMS
                else -> null
            }

        val materialized =
            GeneratedLauncherPageContentItemMaterializer().materialize(plan, appLabelProvider)
        val placedPage =
            if (rejectionReason == null && materialized.skippedItems.isEmpty()) {
                placeItems(page, materialized.items)
            } else {
                null
            }
        return if (placedPage != null) {
            GeneratedLauncherPageContentPlanApplyResult.Applied(page = placedPage)
        } else {
            GeneratedLauncherPageContentPlanApplyResult.Rejected(
                rejectionReason
                    ?: if (materialized.skippedItems.isNotEmpty()) {
                        GeneratedLauncherPageContentPlanApplyRejectionReason.UNSUPPORTED_CONTENT
                    } else {
                        GeneratedLauncherPageContentPlanApplyRejectionReason.INSUFFICIENT_GRID_SPACE
                    },
            )
        }
    }

    private fun placeItems(
        page: LauncherPage,
        items: List<AppShortcutItem>,
    ): LauncherPage? {
        val placementEngine = GridPlacementEngine()
        return items.fold(page.copy(items = emptyList())) { placedPage, item ->
            when (val result = placementEngine.placeItemInFirstAvailableCell(placedPage, item)) {
                is PlaceLauncherItemResult.Placed -> result.page
                is PlaceLauncherItemResult.Rejected -> return null
            }
        }
    }
}

sealed interface GeneratedLauncherPageContentPlanApplyResult {
    data class Applied(val page: LauncherPage) : GeneratedLauncherPageContentPlanApplyResult

    data class Rejected(
        val reason: GeneratedLauncherPageContentPlanApplyRejectionReason,
    ) : GeneratedLauncherPageContentPlanApplyResult
}

enum class GeneratedLauncherPageContentPlanApplyRejectionReason {
    UNAVAILABLE_PLAN,
    NON_GENERATED_PAGE,
    PAGE_KIND_MISMATCH,
    PAGE_HAS_MANUAL_ITEMS,
    UNSUPPORTED_CONTENT,
    INSUFFICIENT_GRID_SPACE,
}
