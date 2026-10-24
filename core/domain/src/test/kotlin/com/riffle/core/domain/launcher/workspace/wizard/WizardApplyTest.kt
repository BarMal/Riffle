package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensLibraryOps
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.editor.EditResult
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEditor
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeView
import com.riffle.core.domain.launcher.workspace.pool.PoolReferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WizardApplyTest {
    private fun finish(
        ctx: WizardContext,
        vararg actions: WizardAction,
    ): WizardState = ctx.run(*actions).also { assertEquals(WizardExit.Finished, it.exit, "the wizard did not finish") }

    private fun applied(
        state: WizardState,
        ctx: WizardContext,
        target: WizardTarget = WizardTarget(ctx.layout, existingPool(), listOf(MAIN_ID, MIGRATED), ids("t")),
    ): WizardResult.Applied = assertIs<WizardResult.Applied>(WizardApply.apply(state, ctx, target))

    private fun assertValid(
        layout: LayoutWorkspaces,
        ctx: WizardContext,
    ) = layout.workspaces.forEach {
        assertEquals(emptyList(), WorkspaceValidation.validate(it, sources = ctx.descriptors), it.name)
    }

    @Test
    fun aPageSetIsAddedAndSavedAsAReferencedLens() {
        val ctx = wizardContext()
        val state =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        val result = applied(state, ctx)
        val page = assertIs<PageSetContainer>(result.layout.find(MAIN_ID)!!.pages.last())
        assertEquals(result.containerId, page.id)
        val lensId = assertNotNull(result.lensId)
        assertEquals(lensId, page.binding.ref)
        assertEquals("Notifications, by app", result.layout.library.find(lensId)?.name)
        assertEquals(result.layout.library.find(lensId)?.lens, page.binding.lens)
        assertEquals(emptyList(), LensLibraryOps.danglingRefs(result.layout))
        assertEquals(existingPool(), result.pool)
        assertValid(result.layout, ctx)
        // The editor can replay the same change in its own session (Undo, Redo, Revert).
        val edit = assertNotNull(result.edit)
        val replayed = WorkspaceEditor.apply(ctx.layout.find(MAIN_ID)!!, edit, ctx.edit)
        assertEquals(result.layout.find(MAIN_ID), assertIs<EditResult.Applied>(replayed).workspace)
    }

    @Test
    fun aWidgetGoesOnTheEditorsPage() {
        val ctx = wizardContext(WizardEntry.Editor(MAIN_ID, ContainerId("grid")))
        val state =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.CALENDAR),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        assertEquals(Destination.WIDGET, state.destination)
        val result = applied(state, ctx)
        val grid = result.layout.find(MAIN_ID)!!.pages.first { it.id == ContainerId("grid") } as PageContainer
        val placed = (grid.content as PageContent.WidgetGrid).placements.single()
        assertEquals(result.containerId, placed.widget.id)
        assertEquals(listOf(SourceIds.CALENDAR), placed.widget.binding.lens.sources)
        assertValid(result.layout, ctx)
    }

    @Test
    fun aDockSectionFromASourcePageIsNotSavedByDefault() {
        val ctx = wizardContext(WizardEntry.FromSource(SourceIds.RECENT_APPS, MAIN_ID))
        val place = ctx.run(WizardAction.Next)
        assertEquals(WizardStep.PLACE, place.step)
        val state =
            finish(
                ctx,
                WizardAction.Next,
                WizardAction.ChooseDestination(Destination.DOCK_SECTION),
                WizardAction.Next,
                WizardAction.Next,
            )
        val result = applied(state, ctx)
        val dock = assertNotNull(result.layout.find(MAIN_ID)!!.dock.dynamicSection)
        assertNull(dock.ref)
        assertNull(result.lensId)
        assertEquals(ctx.layout.library, result.layout.library)
    }

    @Test
    fun aLensOnlyResultChangesOnlyTheLibrary() {
        val ctx = wizardContext(WizardEntry.NewSavedLens)
        val state =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.SEARCH),
                WizardAction.Next,
                WizardAction.SetQuery("weather"),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        val result = applied(state, ctx)
        assertEquals(ctx.layout.workspaces, result.layout.workspaces)
        val saved = result.layout.library.lenses.single()
        assertEquals("weather", saved.lens.parameters[SourceIds.SEARCH]?.text)
        assertNull(result.workspaceId)
    }

    @Test
    fun favouriteAppsAreAddedToTheHomeTheWorkspaceAlreadyShows() {
        val ctx = wizardContext()
        val state =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.FAVOURITE_APPS),
                WizardAction.Next,
                WizardAction.ToggleApp(app("mail")),
                WizardAction.ToggleApp(app("maps")),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        val result = applied(state, ctx)
        // No second home page: the workspace already draws its placed pages.
        assertEquals(ctx.layout, result.layout)
        assertTrue(result.poolChanged)
        val apps = assertNotNull(result.apps)
        assertTrue(apps.forked, "the borrowed home was forked so it stays")
        val arrangement = assertNotNull(result.pool.arrangements[MAIN_ID])
        assertEquals(listOf(MIGRATED_PAGE, LauncherPageId(apps.pageKey!!)), arrangement.pages.map { it.id })
        val shown =
            PoolReferences.referencedIn(
                arrangement,
            ).map { (result.pool.items.getValue(it) as PoolApp).label }.toSet()
        assertEquals(setOf("maps", "mail"), shown, "maps was already there and is not added twice")
        // The migrated arrangement and the dock are untouched.
        assertEquals(existingPool().arrangements[MIGRATED], result.pool.arrangements[MIGRATED])
        assertEquals(existingPool().dock, result.pool.dock)
    }

    @Test
    fun favouriteAppsOnAWorkspaceWithoutAHomeGetAPlacedPage() {
        val bare = LayoutWorkspaces.single(Workspace(MAIN_ID, "Main", listOf(GRID_PAGE)))
        val ctx = wizardContext(layout = bare)
        val state =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.FAVOURITE_APPS),
                WizardAction.Next,
                WizardAction.ToggleApp(app("mail")),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        val result = applied(state, ctx, WizardTarget(bare, existingPool(), listOf(MAIN_ID, MIGRATED), ids("t")))
        val workspace = result.layout.find(MAIN_ID)!!
        val home = workspace.pages.first()
        assertEquals(result.containerId, home.id)
        assertTrue(PoolHomeView.isPlacedHomePage(home))
        val arrangement = assertNotNull(result.pool.arrangements[MAIN_ID])
        assertEquals(
            PoolHomeView.pageKey(home),
            arrangement.pages.single().id.value,
            "only the new page: nothing was borrowed",
        )
        assertNotNull(result.apps).let { assertEquals(false, it.forked) }
        assertValid(result.layout, ctx)
    }

    @Test
    fun staleOrUnfinishedStateIsRejected() {
        val ctx = wizardContext()
        val unfinished = ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS))
        assertIs<WizardResult.Rejected>(WizardApply.apply(unfinished, ctx, WizardTarget(ctx.layout)))
        val done =
            finish(
                ctx,
                WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        // The workspace was deleted meanwhile.
        val gone =
            LayoutWorkspaces.single(
                Workspace(com.riffle.core.domain.launcher.workspace.WorkspaceId("other"), "Other", listOf(GRID_PAGE)),
            )
        assertEquals(
            WizardResult.Rejected(listOf(WizardProblem.NoWorkspace)),
            WizardApply.apply(done, ctx, WizardTarget(gone, ids = ids())),
        )
        // The name was taken meanwhile.
        val taken =
            ctx.layout.copy(
                library =
                    (
                        ctx.layout.library.tryAdd(
                            "Notifications, by app",
                            ContentRecipes.lens(done.setup!!),
                        ) as com.riffle.core.domain.launcher.workspace.LibraryAdd.Added
                    ).library,
            )
        val rejected = assertIs<WizardResult.Rejected>(WizardApply.apply(done, ctx, WizardTarget(taken, ids = ids())))
        assertEquals(
            listOf(WizardProblem.Name(com.riffle.core.domain.launcher.workspace.LibraryProblem.NAME_TAKEN)),
            rejected.problems,
        )
    }
}
