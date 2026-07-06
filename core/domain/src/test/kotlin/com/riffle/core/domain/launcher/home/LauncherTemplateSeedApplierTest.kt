package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LauncherTemplateSeedApplierTest {
    private val applier = LauncherTemplateSeedApplier()
    private val planner = LauncherTemplateSeedPlanner()

    @Test
    fun appliedLayoutPreservesPlannedPageOrderAndTypes() {
        val plan =
            planFor(
                seedPageTypes =
                    listOf(
                        LauncherPageType.Home,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        LauncherPageType.AllApps,
                    ),
            )

        val layout = appliedLayout(plan)

        assertEquals(plan.pages.map { page -> page.id }, layout.pages.map { page -> page.id })
        assertEquals(plan.pageTypes, layout.pages.map { page -> page.type })
        assertTrue(layout.pages.all { page -> page.items.isEmpty() })
    }

    @Test
    fun appliedLayoutPreservesStaticAndGeneratedSeedPageIds() {
        val plan =
            planFor(
                seedPageTypes =
                    listOf(
                        LauncherPageType.Home,
                        LauncherPageType.Home,
                        LauncherPageType.AllApps,
                        LauncherPageType.AllApps,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                    ),
            )

        val layout = appliedLayout(plan)

        assertEquals(
            listOf(
                LauncherPageId("home"),
                LauncherPageId("home:2"),
                LauncherPageId("all-apps"),
                LauncherPageId("all-apps:2"),
                LauncherPageId("generated:today:1"),
                LauncherPageId("generated:today:2"),
            ),
            layout.pages.map { page -> page.id },
        )
    }

    @Test
    fun appliedLayoutSelectsFirstPlannedPage() {
        val plan =
            planFor(
                seedPageTypes =
                    listOf(
                        LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES),
                        LauncherPageType.Home,
                    ),
            )

        val layout = appliedLayout(plan)

        assertEquals(plan.pages.first().id, layout.selectedPageId)
        assertSame(layout.pages.first(), layout.selectedPage)
        assertEquals(0, layout.selectedPageIndex)
    }

    @Test
    fun appliedLayoutUsesTargetKeyViewModeAndDeviceClassDefaults() {
        val targetKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            )
        val plan =
            planFor(
                targetKey = targetKey,
                viewModes = setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                deviceClasses = setOf(HomeLayoutDeviceClass.TABLET),
                seedPageTypes =
                    listOf(
                        LauncherPageType.Home,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY),
                    ),
            )

        val layout = appliedLayout(plan)
        val expectedDefaults = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.TABLET)

        assertEquals(targetKey.viewMode, layout.viewMode)
        assertEquals(expectedDefaults.settings, layout.settings)
        assertEquals(expectedDefaults.dock, layout.dock)
        assertEquals(
            List(layout.pages.size) { expectedDefaults.settings.grid.dimensions },
            layout.pages.map { page -> page.grid },
        )
    }

    @Test
    fun emptyPlanIsRejected() {
        val rejected =
            assertIs<LauncherTemplateSeedApplyResult.Rejected>(
                applier.apply(
                    LauncherTemplateSeedPlan(
                        templateId = LauncherTemplateId("empty"),
                        targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                        pages = emptyList(),
                    ),
                ),
            )

        assertEquals(LauncherTemplateSeedApplyRejectionReason.EMPTY_PLAN, rejected.reason)
    }

    private fun appliedLayout(plan: LauncherTemplateSeedPlan): HomeLayout =
        assertIs<LauncherTemplateSeedApplyResult.Applied>(applier.apply(plan)).layout

    private fun planFor(
        targetKey: HomeLayoutKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
        viewModes: Set<LauncherViewMode> = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
        deviceClasses: Set<HomeLayoutDeviceClass> = setOf(HomeLayoutDeviceClass.PHONE),
        seedPageTypes: List<LauncherPageType>,
    ): LauncherTemplateSeedPlan =
        assertIs<LauncherTemplateSeedPlanResult.Planned>(
            planner.plan(
                template =
                    LauncherTemplate(
                        id = LauncherTemplateId("template"),
                        metadata =
                            LauncherTemplateMetadata(
                                displayName = "Template",
                                description = "Template seed",
                            ),
                        supportedViewModes = viewModes,
                        supportedDeviceClasses = deviceClasses,
                        seedPageTypes = seedPageTypes,
                    ),
                targetKey = targetKey,
            ),
        ).plan
}
