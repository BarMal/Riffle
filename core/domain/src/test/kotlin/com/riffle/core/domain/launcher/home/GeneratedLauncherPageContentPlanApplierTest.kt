package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GeneratedLauncherPageContentPlanApplierTest {
    private val applier = GeneratedLauncherPageContentPlanApplier()

    @Test
    fun rejectsGeneratedPageWithManualItems() {
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

        val rejected =
            assertIs<GeneratedLauncherPageContentPlanApplyResult.Rejected>(
                apply(plan = plan(kind = GeneratedLauncherPageKind.TODAY), page = page),
            )

        assertEquals(GeneratedLauncherPageContentPlanApplyRejectionReason.PAGE_HAS_MANUAL_ITEMS, rejected.reason)
        assertEquals(1, page.items.size)
    }

    @Test
    fun unavailablePlanIsRejected() {
        val rejected =
            assertIs<GeneratedLauncherPageContentPlanApplyResult.Rejected>(
                apply(
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
    fun materializesAppContentIntoGeneratedPage() {
        val app =
            GeneratedLauncherPageContentItem.App(
                AppIdentity(AppPackageName("com.riffle.calendar"), AppActivityName(".MainActivity")),
            )

        val applied =
            appliedPage(
                plan = plan(kind = GeneratedLauncherPageKind.APP, items = listOf(app)),
                page = generatedPage(kind = GeneratedLauncherPageKind.APP),
            )

        assertEquals(listOf(app.identity), applied.items.filterIsInstance<AppShortcutItem>().map { it.appIdentity })
    }

    @Test
    fun materializedAppsOccupyDistinctGridCellsInReadingOrder() {
        val apps =
            listOf(
                "calendar",
                "camera",
                "clock",
            ).map { packageSuffix ->
                GeneratedLauncherPageContentItem.App(
                    AppIdentity(AppPackageName("com.riffle.$packageSuffix"), AppActivityName(".MainActivity")),
                )
            }

        val applied =
            appliedPage(
                plan(kind = GeneratedLauncherPageKind.CATEGORY, items = apps),
                generatedPage(
                    kind = GeneratedLauncherPageKind.CATEGORY,
                    grid = GridDimensions(columns = 2, rows = 2),
                ),
            )

        assertEquals(
            listOf(
                GridCell(column = 0, row = 0),
                GridCell(column = 1, row = 0),
                GridCell(column = 0, row = 1),
            ),
            applied.items.map { item -> requireNotNull(item.placement).cell },
        )
    }

    @Test
    fun replacesExistingGeneratedContent() {
        val oldApp =
            GeneratedLauncherPageContentItem.App(
                AppIdentity(AppPackageName("com.riffle.old"), AppActivityName(".MainActivity")),
            )
        val newApp =
            GeneratedLauncherPageContentItem.App(
                AppIdentity(AppPackageName("com.riffle.new"), AppActivityName(".MainActivity")),
            )
        val existing =
            appliedPage(
                plan(GeneratedLauncherPageKind.APP, items = listOf(oldApp)),
                generatedPage(kind = GeneratedLauncherPageKind.APP),
            )

        val refreshed = appliedPage(plan(GeneratedLauncherPageKind.APP, items = listOf(newApp)), existing)

        assertEquals(
            listOf(newApp.identity),
            refreshed.items.filterIsInstance<AppShortcutItem>().map { it.appIdentity },
        )
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
                apply(
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
                apply(
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
            apply(plan = plan, page = page),
        ).page

    private fun apply(
        plan: GeneratedLauncherPageContentPlan,
        page: LauncherPage,
    ) = applier.apply(plan, page) { app -> app.identity.packageName.value }

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
        items: List<GeneratedLauncherPageContentItem> = emptyList(),
    ): GeneratedLauncherPageContentPlan =
        GeneratedLauncherPageContentPlan(
            pageId = kind.spec.defaultPageId(),
            kind = kind,
            canCreate = canCreate,
            requiredDataSources = kind.spec.requiredDataSources,
            items = items,
        )
}
