package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.contextual.ContextualBehaviorSelector
import com.riffle.core.domain.launcher.contextual.ContextualSignalPlanInput
import com.riffle.core.domain.launcher.contextual.ContextualSignalPlanner
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanApplier
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanApplyRejectionReason
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanApplyResult
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanner
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageType

@Suppress("MaxLineLength")
internal fun LauncherShellState.withRefreshedGeneratedPages(homeLayoutRepository: HomeLayoutRepository): LauncherShellState {
    val layout = refreshedGeneratedPages(homeLayout)
    return if (layout == homeLayout) this else withHomeLayout(layout, homeLayoutRepository)
}

internal fun LauncherShellState.refreshedGeneratedPages(layout: HomeLayout): HomeLayout {
    val planner = GeneratedLauncherPageContentPlanner()
    val applier = GeneratedLauncherPageContentPlanApplier()
    val labels = installedApps.associate { app -> app.identity to app.label }
    return layout
        .copy(
            pages =
                layout.pages.map { page ->
                    val type =
                        page.type as? LauncherPageType.Generated
                            ?: return@map page
                    if (type.kind !in appBackedGeneratedPageKinds) return@map page
                    val result =
                        applier.apply(
                            plan = planner.plan(type.kind, input = generatedPageInput(), pageId = page.id),
                            page = page,
                            appLabelProvider = { item -> labels[item.identity].orEmpty() },
                        )
                    when (result) {
                        is GeneratedLauncherPageContentPlanApplyResult.Applied -> result.page
                        is GeneratedLauncherPageContentPlanApplyResult.Rejected ->
                            when (result.reason) {
                                GeneratedLauncherPageContentPlanApplyRejectionReason.UNAVAILABLE_PLAN ->
                                    page.copy(items = emptyList(), generatedContentOverflowCount = 0)

                                else -> page
                            }
                    }
                },
        ).withContextualGeneratedPageSelected(contextualGeneratedPageKinds())
}

private fun LauncherShellState.generatedPageInput() =
    com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanInput(
        installedApps = installedApps,
        appCategoriesAvailable = installedApps.any { app -> !app.category.isNullOrBlank() },
    )

private fun LauncherShellState.contextualGeneratedPageKinds(): List<GeneratedLauncherPageKind> =
    ContextualBehaviorSelector
        .select(
            settings = launcherSettings.contextual,
            signals =
                ContextualSignalPlanner.plan(
                    ContextualSignalPlanInput(
                        personalInstalledAppCount = installedApps.countProfile(AppProfileType.PERSONAL),
                        workInstalledAppCount = installedApps.countProfile(AppProfileType.WORK),
                        notificationGroupCount = notificationGroupsByApp.size,
                        notificationCount = notificationCountsByCategory.values.sum(),
                    ),
                ),
        ).pageKinds

@Suppress("MaxLineLength")
private fun List<InstalledApp>.countProfile(type: AppProfileType): Int = count { app -> app.identity.profile.type == type }

@Suppress("MaxLineLength")
private fun HomeLayout.withContextualGeneratedPageSelected(contextualPageKinds: List<GeneratedLauncherPageKind>): HomeLayout {
    val pageId =
        contextualPageKinds.firstNotNullOfOrNull { kind ->
            pages.firstOrNull { page -> (page.type as? LauncherPageType.Generated)?.kind == kind }?.id
        } ?: return this
    return if (pageId == selectedPageId) this else copy(selectedPageId = pageId)
}

private val appBackedGeneratedPageKinds =
    setOf(
        GeneratedLauncherPageKind.APP,
        GeneratedLauncherPageKind.TODAY,
        GeneratedLauncherPageKind.WORK,
        GeneratedLauncherPageKind.PERSONAL,
        GeneratedLauncherPageKind.CATEGORY,
    )
