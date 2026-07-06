package com.riffle.core.domain.launcher.home

class LauncherTemplateSeedApplier {
    fun apply(plan: LauncherTemplateSeedPlan): LauncherTemplateSeedApplyResult {
        if (plan.pages.isEmpty()) {
            return LauncherTemplateSeedApplyResult.Rejected(LauncherTemplateSeedApplyRejectionReason.EMPTY_PLAN)
        }

        val defaults = HomeLayoutDefaults.standard(plan.targetKey.deviceClass)
        val pages =
            plan.pages.map { page ->
                LauncherPage(
                    id = page.id,
                    type = page.type,
                    grid = defaults.settings.grid.dimensions,
                )
            }

        return LauncherTemplateSeedApplyResult.Applied(
            layout =
                defaults.copy(
                    viewMode = plan.targetKey.viewMode,
                    pages = pages,
                    selectedPageId = pages.first().id,
                ),
        )
    }
}

sealed interface LauncherTemplateSeedApplyResult {
    data class Applied(val layout: HomeLayout) : LauncherTemplateSeedApplyResult

    data class Rejected(val reason: LauncherTemplateSeedApplyRejectionReason) : LauncherTemplateSeedApplyResult
}

enum class LauncherTemplateSeedApplyRejectionReason {
    EMPTY_PLAN,
}
