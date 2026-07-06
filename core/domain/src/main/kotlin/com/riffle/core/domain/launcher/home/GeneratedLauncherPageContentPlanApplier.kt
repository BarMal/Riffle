package com.riffle.core.domain.launcher.home

class GeneratedLauncherPageContentPlanApplier {
    fun apply(
        plan: GeneratedLauncherPageContentPlan,
        page: LauncherPage,
    ): GeneratedLauncherPageContentPlanApplyResult {
        val pageType = page.type
        val rejectionReason =
            when {
                !plan.canCreate -> GeneratedLauncherPageContentPlanApplyRejectionReason.UNAVAILABLE_PLAN
                pageType !is LauncherPageType.Generated ->
                    GeneratedLauncherPageContentPlanApplyRejectionReason.NON_GENERATED_PAGE
                pageType.kind != plan.kind -> GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_KIND_MISMATCH
                else -> null
            }

        return if (rejectionReason == null) {
            GeneratedLauncherPageContentPlanApplyResult.Applied(
                page =
                    page.copy(
                        items = emptyList(),
                    ),
            )
        } else {
            GeneratedLauncherPageContentPlanApplyResult.Rejected(rejectionReason)
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
}
