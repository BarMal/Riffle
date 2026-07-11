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
                page.items.isNotEmpty() ->
                    GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_HAS_MANUAL_ITEMS
                else -> null
            }

        val materialized =
            GeneratedLauncherPageContentItemMaterializer().materialize(plan, appLabelProvider)
        return if (rejectionReason == null && materialized.skippedItems.isEmpty()) {
            GeneratedLauncherPageContentPlanApplyResult.Applied(
                page = page.copy(items = materialized.items),
            )
        } else {
            GeneratedLauncherPageContentPlanApplyResult.Rejected(
                rejectionReason ?: GeneratedLauncherPageContentPlanApplyRejectionReason.UNSUPPORTED_CONTENT,
            )
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
}
