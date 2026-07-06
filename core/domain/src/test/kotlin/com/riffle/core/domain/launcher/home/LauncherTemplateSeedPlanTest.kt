package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LauncherTemplateSeedPlanTest {
    private val planner = LauncherTemplateSeedPlanner()

    @Test
    fun compatiblePlanPreservesSeedOrder() {
        val template =
            template(
                seedPageTypes =
                    listOf(
                        LauncherPageType.Home,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        LauncherPageType.AllApps,
                    ),
            )

        val planned =
            assertIs<LauncherTemplateSeedPlanResult.Planned>(
                planner.plan(
                    template = template,
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            )

        assertEquals(template.id, planned.plan.templateId)
        assertEquals(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER), planned.plan.targetKey)
        assertEquals(template.seedPageTypes, planned.plan.pageTypes)
        assertEquals(
            listOf(
                LauncherPageId("home"),
                LauncherPageId("generated:today:1"),
                LauncherPageId("all-apps"),
            ),
            planned.plan.pages.map { page -> page.id },
        )
    }

    @Test
    fun incompatibleViewModeRejected() {
        val template =
            template(
                viewModes = setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
            )

        val rejected =
            assertIs<LauncherTemplateSeedPlanResult.Rejected>(
                planner.plan(
                    template = template,
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            )

        assertEquals(LauncherTemplateSeedPlanRejectionReason.INCOMPATIBLE_VIEW_MODE, rejected.reason)
    }

    @Test
    fun incompatibleDeviceClassRejected() {
        val template =
            template(
                deviceClasses = setOf(HomeLayoutDeviceClass.TABLET),
            )

        val rejected =
            assertIs<LauncherTemplateSeedPlanResult.Rejected>(
                planner.plan(
                    template = template,
                    targetKey =
                        HomeLayoutKey(
                            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                            deviceClass = HomeLayoutDeviceClass.PHONE,
                        ),
                ),
            )

        assertEquals(LauncherTemplateSeedPlanRejectionReason.INCOMPATIBLE_DEVICE_CLASS, rejected.reason)
    }

    @Test
    fun emptySeedPageListRejected() {
        val template = template(seedPageTypes = emptyList())

        val rejected =
            assertIs<LauncherTemplateSeedPlanResult.Rejected>(
                planner.plan(
                    template = template,
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            )

        assertEquals(LauncherTemplateSeedPlanRejectionReason.EMPTY_SEED_PAGES, rejected.reason)
    }

    @Test
    fun generatedPageSeedGetsDeterministicId() {
        val template =
            template(
                seedPageTypes =
                    listOf(
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES),
                    ),
            )

        val first =
            assertIs<LauncherTemplateSeedPlanResult.Planned>(
                planner.plan(
                    template = template,
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            ).plan
        val second =
            assertIs<LauncherTemplateSeedPlanResult.Planned>(
                planner.plan(
                    template = template,
                    targetKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER),
                ),
            ).plan

        assertEquals(
            listOf(
                LauncherPageId("generated:today:1"),
                LauncherPageId("generated:today:2"),
                LauncherPageId("generated:favourites:1"),
            ),
            first.pages.map { page -> page.id },
        )
        assertEquals(first.pages, second.pages)
    }

    private fun template(
        id: String = "seeded-template",
        viewModes: Set<LauncherViewMode> = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
        deviceClasses: Set<HomeLayoutDeviceClass> = setOf(HomeLayoutDeviceClass.PHONE),
        seedPageTypes: List<LauncherPageType> = listOf(LauncherPageType.Home),
    ): LauncherTemplate =
        LauncherTemplate(
            id = LauncherTemplateId(id),
            metadata =
                LauncherTemplateMetadata(
                    displayName = id,
                    description = "$id template",
                ),
            supportedViewModes = viewModes,
            supportedDeviceClasses = deviceClasses,
            seedPageTypes = seedPageTypes,
        )
}
