package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceBindings
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalkthroughApplyTest {
    private val home = WalkthroughFlow.WORKSPACE_ID

    private fun target(ctx: WalkthroughContext) =
        WalkthroughTarget(
            ctx.layout,
            ctx.pool,
            listOf(home, MIGRATED),
            ids("t"),
        )

    private fun apply(
        state: WalkthroughState,
        ctx: WalkthroughContext,
        target: WalkthroughTarget = target(ctx),
    ): WalkthroughResult.Applied = assertIs<WalkthroughResult.Applied>(WalkthroughApply.apply(state, ctx, target))

    private fun labels(
        pool: PlacedItemPool,
        ids: Collection<com.riffle.core.domain.launcher.workspace.pool.PoolItemId>,
    ) = ids.map { (pool.items.getValue(it) as PoolApp).label }

    @Test
    fun aFreshInstallReplacesTheSeededDefault() {
        val ctx = walkthroughContext()
        val seed = ctx.layout.activeId
        val result = apply(ctx.toReview(), ctx)
        assertEquals(listOf(seed), result.removed)
        assertEquals(listOf(home), result.layout.workspaces.map { it.id })
        assertEquals(home, result.layout.activeId)
        assertEquals(home, result.layout.defaultId)
        val workspace = result.layout.active
        assertEquals("My home", workspace.name)
        assertEquals(emptyList(), WorkspaceValidation.validate(workspace, sources = ctx.descriptors))
        assertTrue(WorkspaceHome.hasHomeGrid(workspace))
        // Favourite apps went into the pool on their own page; nothing was borrowed.
        val arrangement = assertNotNull(result.pool.arrangements[home])
        assertEquals(listOf(WalkthroughBuilder.APPS_PAGE), arrangement.pages.map { it.id })
        assertEquals(
            listOf("phone", "mail"),
            labels(result.pool, arrangement.pages.single().placements.map { it.item }),
        )
        // The dock became the picked apps.
        assertEquals(listOf("phone", "camera"), labels(result.pool, result.pool.dock!!.pins))
        assertEquals(WalkthroughUndo(ctx.layout, ctx.pool), result.undo)
    }

    @Test
    fun anExistingUserLosesNothing() {
        val ctx = walkthroughContext(OnboardingOrigin.EXISTING_USER)
        val result = apply(ctx.toReview(), ctx)
        assertEquals(emptyList(), result.removed)
        ctx.layout.workspaces.forEach { assertEquals(it, result.layout.find(it.id), "${it.name} changed") }
        assertEquals(ctx.layout.workspaces.map { it.id } + home, result.layout.workspaces.map { it.id })
        assertEquals(home, result.layout.activeId)
        assertEquals(ctx.layout.defaultId, result.layout.defaultId)
        assertEquals(ctx.layout.library, result.layout.library)
        // "Widgets from my home" borrows the current home: the pool is exactly as it was.
        assertEquals(ctx.pool, result.pool)
        assertTrue(WorkspaceHome.hasHomeGrid(result.layout.active))
    }

    @Test
    fun anExistingUserAddingFavouritesKeepsTheirHomeAndDock() {
        val ctx = walkthroughContext(OnboardingOrigin.EXISTING_USER)
        var state = ctx.run(WalkthroughAction.Next, WalkthroughAction.ToggleGoal(ContentGoalId.FAVOURITE_APPS))
        assertTrue(ContentGoalId.FAVOURITE_APPS in state.chosen)
        while (state.step != WalkthroughStep.DOCK) state = ctx.run(WalkthroughAction.Next, from = state)
        state =
            ctx.run(
                WalkthroughAction.ToggleDockPin(app("phone")),
                WalkthroughAction.ToggleDockPin(app("camera")),
                from = state,
            )
        val review = ctx.toReview(state)
        val result = apply(review, ctx)
        val arrangement = assertNotNull(result.pool.arrangements[home])
        assertEquals(
            listOf(MIGRATED_PAGE, WalkthroughBuilder.APPS_PAGE),
            arrangement.pages.map {
                it.id
            },
            "the home was forked, then the page added",
        )
        assertEquals(ctx.pool.arrangements[MIGRATED], result.pool.arrangements[MIGRATED])
        ctx.pool.items.keys.forEach { assertTrue(it in result.pool.items, "$it was lost") }
        // The dock only gained what it lacked.
        assertEquals(listOf("phone", "camera"), labels(result.pool, result.pool.dock!!.pins))
        assertEquals(listOf(app("camera")), result.dockPinsAdded)
    }

    @Test
    fun applyingTwiceIsIdempotent() {
        listOf(OnboardingOrigin.FRESH_INSTALL, OnboardingOrigin.EXISTING_USER).forEach { origin ->
            val ctx = walkthroughContext(origin)
            val withFavourites =
                ctx.run(WalkthroughAction.Next).let { goals ->
                    if (ContentGoalId.FAVOURITE_APPS in goals.chosen) {
                        goals
                    } else {
                        ctx.run(
                            WalkthroughAction.ToggleGoal(ContentGoalId.FAVOURITE_APPS),
                            from = goals,
                        )
                    }
                }
            val review = ctx.toReview(withFavourites)
            val first = apply(review, ctx)
            val again =
                apply(review, ctx, WalkthroughTarget(first.layout, first.pool, listOf(home, MIGRATED), ids("u")))
            assertEquals(first.layout, again.layout, "$origin layout")
            assertEquals(first.pool, again.pool, "$origin pool")
            assertEquals(emptyList(), again.removed)
        }
    }

    @Test
    fun aRerunUpdatesMyHomeInPlaceAndReplacesNothing() {
        val ctx = walkthroughContext()
        val first = apply(ctx.toReview(), ctx)
        // The user later installs the Nova preset again and the dock changes.
        val nova =
            WorkspacePresets.installPreset(
                WorkspacePresets.nova,
                com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.PHONE,
                ids("nova"),
            ).copy(presetId = WorkspacePresets.DEFAULT_ID)
        val layout = first.layout.add(nova)
        val rerun = walkthroughContext(firstRun = false).copy(layout = layout, pool = first.pool)
        val state =
            rerun.run(
                WalkthroughAction.Next,
                WalkthroughAction.ToggleGoal(ContentGoalId.CALENDAR),
                WalkthroughAction.ToggleGoal(ContentGoalId.SEARCH),
            )
        val result = apply(rerun.toReview(state), rerun)
        assertEquals(emptyList(), result.removed)
        assertEquals(nova, result.layout.find(nova.id))
        assertEquals(listOf(home, nova.id), result.layout.workspaces.map { it.id })
        val pages = result.layout.find(home)!!.pages.map { it.id.value }
        assertTrue(pages.any { it.endsWith(":search") } && pages.none { it.endsWith(":widgets") }, "$pages")
        assertEquals(first.pool.dock, result.pool.dock, "a re-run only adds to the dock")
    }

    @Test
    fun skippedPermissionsLeaveTheContentInPlaceShowingNeedsAccess() {
        val ctx = walkthroughContext(access = NOTHING_GRANTED)
        var state = WalkthroughFlow.start(ctx)
        while (state.step != WalkthroughStep.PERMISSIONS) state = ctx.run(WalkthroughAction.Next, from = state)
        state =
            ctx.run(
                WalkthroughAction.SkipPermission(SourcePermission.NOTIFICATION_ACCESS),
                WalkthroughAction.SkipPermission(SourcePermission.CALENDAR),
                WalkthroughAction.Next,
                from = state,
            )
        assertEquals(WalkthroughStep.REVIEW, state.step)
        val result = apply(state, ctx)
        val workspace = result.layout.active
        val sources = WorkspaceBindings.sites(workspace).flatMap { it.binding.lens.sources }.toSet()
        assertTrue(SourceIds.NOTIFICATIONS in sources && SourceIds.CALENDAR in sources, "$sources")
        assertTrue(workspace.pages.any { it is PageSetContainer && SourceIds.NOTIFICATIONS in it.binding.lens.sources })
        val widgets =
            workspace.pages.filterIsInstance<PageContainer>().mapNotNull { it.content as? PageContent.WidgetGrid }
        assertTrue(widgets.single().placements.any { SourceIds.CALENDAR in it.widget.binding.lens.sources })
        assertEquals(
            listOf(ContentGoalId.CALENDAR, ContentGoalId.NOTIFICATIONS),
            WalkthroughFlow.review(state, ctx).needsAccess,
        )
    }

    @Test
    fun undoRestoresWhatWasStored() {
        val ctx = walkthroughContext(OnboardingOrigin.EXISTING_USER)
        val result = apply(ctx.toReview(), ctx)
        assertEquals(ctx.layout, result.undo.layout)
        assertEquals(ctx.pool, result.undo.pool)
    }

    @Test
    fun aDockThatIsNotInThePoolYetIsLeftAlone() {
        val ctx = walkthroughContext().copy(pool = freshPool().copy(dock = null))
        val result = apply(ctx.toReview(), ctx)
        assertTrue(result.dockSkipped)
        assertEquals(null, result.pool.dock)
    }

    @Test
    fun widgetsShareAWidgetsPageAndTheStartPageIsKept() {
        val ctx = walkthroughContext()
        val arrange =
            ctx.run(
                WalkthroughAction.Next,
                WalkthroughAction.ToggleGoal(ContentGoalId.MEDIA),
                WalkthroughAction.ToggleGoal(ContentGoalId.RECENT_APPS),
                WalkthroughAction.Next,
                WalkthroughAction.SetStartGoal(ContentGoalId.MY_APPS),
            )
        val built = WalkthroughBuilder.build(ctx.toReview(arrange), ctx)
        val widgetPages = built.workspace.pages.filter { (it as? PageContainer)?.content is PageContent.WidgetGrid }
        assertEquals(
            listOf(
                ContentGoalId.MEDIA,
                ContentGoalId.CALENDAR,
                ContentGoalId.RECENT_APPS,
            ).toSet(),
            widgetPages.flatMap {
                built.goalsByPage.getValue(it.id)
            }.toSet(),
        )
        assertEquals(emptyList(), WorkspaceValidation.validate(built.workspace, sources = ctx.descriptors))
        assertEquals(
            built.goalsByPage.entries.first { ContentGoalId.MY_APPS in it.value }.key,
            built.workspace.startPageId,
        )
    }
}
