package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanApplier
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanApplyResult
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanner
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherPageType

@Suppress("MaxLineLength")
internal fun LauncherShellState.withRefreshedGeneratedPages(homeLayoutRepository: HomeLayoutRepository): LauncherShellState {
    val planner = GeneratedLauncherPageContentPlanner()
    val applier = GeneratedLauncherPageContentPlanApplier()
    val labels = installedApps.associate { app -> app.identity to app.label }
    val layout =
        homeLayout.copy(
            pages =
                homeLayout.pages.map { page ->
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
                        is GeneratedLauncherPageContentPlanApplyResult.Rejected -> page
                    }
                },
        )
    return if (layout == homeLayout) this else withHomeLayout(layout, homeLayoutRepository)
}

private fun LauncherShellState.generatedPageInput() =
    com.riffle.core.domain.launcher.home.GeneratedLauncherPageContentPlanInput(installedApps = installedApps)

private val appBackedGeneratedPageKinds =
    setOf(
        GeneratedLauncherPageKind.APP,
        GeneratedLauncherPageKind.TODAY,
        GeneratedLauncherPageKind.WORK,
        GeneratedLauncherPageKind.PERSONAL,
    )
