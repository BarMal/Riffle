package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeneratedLauncherPageContentPlanApplierTest {
    private val applier = GeneratedLauncherPageContentPlanApplier()

    @Test
    fun acceptedGeneratedPageProducesEmptyPageItemsAndPreservesDescriptorShape() {
        val page =
            generatedPage(
                kind = GeneratedLauncherPageKind.TODAY,
                items =
                    listOf(
                        WidgetItem(
                            id = LauncherItemId("legacy-widget"),
                            appWidgetId = HostedWidgetId(7),
                            label = "Legacy widget",
                        ),
                    ),
            )

        val applied = appliedPage(plan(kind = GeneratedLauncherPageKind.TODAY), page)

        assertEquals(page.id, applied.id)
        assertEquals(page.type, applied.type)
        assertEquals(page.grid, applied.grid)
        assertEquals(emptyList(), applied.items)
    }

    @Test
    fun unavailablePlanIsRejected() {
        val rejected =
            assertIs<GeneratedLauncherPageContentPlanApplyResult.Rejected>(
                applier.apply(
                    plan = plan(kind = GeneratedLauncherPageKind.FAVOURITES, canCreate = false),
                    page = generatedPage(kind = GeneratedLauncherPageKind.FAVOURITES),
                ),
            )

        assertEquals(GeneratedLauncherPageContentPlanApplyRejectionReason.UNAVAILABLE_PLAN, rejected.reason)
    }

    @Test
    fun customPageIdIsPreserved() {
        val page =
            generatedPage(
                id = LauncherPageId("custom-generated-page"),
                kind = GeneratedLauncherPageKind.APP,
            )

        val applied = appliedPage(plan(kind = GeneratedLauncherPageKind.APP), page)

        assertEquals(LauncherPageId("custom-generated-page"), applied.id)
    }

    @Test
    fun pageGridIsPreserved() {
        val grid = GridDimensions(columns = 7, rows = 5)
        val page =
            generatedPage(
                kind = GeneratedLauncherPageKind.NOTIFICATION_CARDS,
                grid = grid,
            )

        val applied = appliedPage(plan(kind = GeneratedLauncherPageKind.NOTIFICATION_CARDS), page)

        assertEquals(grid, applied.grid)
    }

    @Test
    fun nonGeneratedPageIsRejected() {
        val rejected =
            assertIs<GeneratedLauncherPageContentPlanApplyResult.Rejected>(
                applier.apply(
                    plan = plan(kind = GeneratedLauncherPageKind.TODAY),
                    page =
                        LauncherPage(
                            id = LauncherPageId("home"),
                            type = LauncherPageType.Home,
                            grid = GridDimensions(columns = 4, rows = 5),
                        ),
                ),
            )

        assertEquals(GeneratedLauncherPageContentPlanApplyRejectionReason.NON_GENERATED_PAGE, rejected.reason)
    }

    @Test
    fun generatedPageKindMismatchIsRejected() {
        val rejected =
            assertIs<GeneratedLauncherPageContentPlanApplyResult.Rejected>(
                applier.apply(
                    plan = plan(kind = GeneratedLauncherPageKind.WORK),
                    page = generatedPage(kind = GeneratedLauncherPageKind.PERSONAL),
                ),
            )

        assertEquals(GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_KIND_MISMATCH, rejected.reason)
    }

    private fun appliedPage(
        plan: GeneratedLauncherPageContentPlan,
        page: LauncherPage,
    ): LauncherPage =
        assertIs<GeneratedLauncherPageContentPlanApplyResult.Applied>(
            applier.apply(plan = plan, page = page),
        ).page

    private fun generatedPage(
        id: LauncherPageId = LauncherPageId("generated-page"),
        kind: GeneratedLauncherPageKind,
        grid: GridDimensions = GridDimensions(columns = 4, rows = 5),
        items: List<LauncherItem> = emptyList(),
    ): LauncherPage =
        LauncherPage(
            id = id,
            type = LauncherPageType.Generated(kind),
            grid = grid,
            items = items,
        )

    private fun plan(
        kind: GeneratedLauncherPageKind,
        canCreate: Boolean = true,
    ): GeneratedLauncherPageContentPlan =
        GeneratedLauncherPageContentPlan(
            pageId = kind.spec.defaultPageId(),
            kind = kind,
            canCreate = canCreate,
            requiredDataSources = kind.spec.requiredDataSources,
        )
}
