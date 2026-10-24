package com.riffle.app.launcher.wizard

import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.app.launcher.wizard.WizardTestFixtures.context
import com.riffle.app.launcher.wizard.WizardTestFixtures.homeId
import com.riffle.app.launcher.wizard.WizardTestFixtures.phone
import com.riffle.app.launcher.wizard.WizardTestFixtures.run
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardEntry
import com.riffle.core.domain.launcher.workspace.wizard.WizardExit
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardCommitTest {
    private val placement = WizardPlacement(phone, ids = WizardTestFixtures.ids("w"))

    /** Favourite apps with one app picked: the goal, then the Content step's picker. */
    private fun favourites(): Array<WizardAction> =
        arrayOf(
            WizardAction.ChooseGoal(ContentGoalId.FAVOURITE_APPS),
            WizardAction.Next,
            WizardAction.ToggleApp(WizardTestFixtures.app("maps")),
        )

    /** [choices], then Next until the run finishes. */
    private fun finish(
        context: WizardContext,
        vararg choices: WizardAction,
    ): WizardState {
        var state = run(context, WizardFlow.start(context), *choices)
        repeat(MAX_STEPS) { if (state.exit == null) state = run(context, state, WizardAction.Next) }
        assertEquals(WizardExit.Finished, state.exit)
        return state
    }

    @Test
    fun useThisSourceSavesThePageToTheActiveWorkspace() {
        val stores = FakeWizardStores()
        val context = context(entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, homeId))
        val finished = finish(context)

        val result = WizardCommit.commit(finished, context, stores, placement)

        val saved = result as WizardCommitResult.Saved
        assertEquals("Notifications", saved.name)
        val home = checkNotNull(stores.set?.workspacesFor(phone)?.find(homeId))
        assertEquals(2, home.pages.size)
        // Not saved to the library from a source page (save is off by default there).
        assertTrue(stores.set?.workspacesFor(phone)?.library?.lenses.orEmpty().isEmpty())
        assertTrue(stores.savedPools.isEmpty())
    }

    @Test
    fun aNewSavedLensOnlyAddsToTheLibrary() {
        val stores = FakeWizardStores()
        val context = context(entry = WizardEntry.NewSavedLens)
        val finished = finish(context, WizardAction.ChooseGoal(ContentGoalId.MY_APPS))

        val saved = WizardCommit.commit(finished, context, stores, placement) as WizardCommitResult.Saved

        val layout = checkNotNull(stores.set).workspacesFor(phone)
        assertEquals(listOf("My apps, grid"), layout.library.lenses.map { it.name })
        assertEquals(WizardTestFixtures.layout.workspaces, layout.workspaces)
        assertEquals("My apps, grid", saved.name)
    }

    @Test
    fun favouriteAppsGoIntoThePoolAndTheWorkspaceGetsAPlacedPage() {
        val stores = FakeWizardStores()
        val context = context(entry = WizardEntry.FromSource(WorkspaceSourceIds.HOME_GRID, homeId))
        val finished = finish(context, *favourites())

        WizardCommit.commit(finished, context, stores, placement) as WizardCommitResult.Saved

        assertEquals(1, stores.savedPools.size)
        val home = checkNotNull(stores.set?.workspacesFor(phone)?.find(homeId))
        val first = home.pages.first() as PageContainer
        val lens = (first.content as PageContent.Bound).binding.lens
        assertEquals(listOf(WorkspaceSourceIds.HOME_GRID), lens.sources)
    }

    @Test
    fun aReadOnlyHomeRefusesPickedAppsWithTheExistingNoticeAndWritesNothing() {
        val stores = FakeWizardStores(homeReadOnly = true)
        val context = context(entry = WizardEntry.FromSource(WorkspaceSourceIds.HOME_GRID, homeId))
        val finished = finish(context, *favourites())

        val result = WizardCommit.commit(finished, context, stores, placement)

        assertEquals(WizardCommitResult.Refused(READ_ONLY_HOME_NOTICE), result)
        assertTrue(stores.savedSets.isEmpty())
        assertTrue(stores.savedPools.isEmpty())
    }

    @Test
    fun aReadOnlyHomeStillTakesALensPage() {
        val stores = FakeWizardStores(homeReadOnly = true)
        val context = context(entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, homeId))

        val result = WizardCommit.commit(finish(context), context, stores, placement)

        assertTrue(result is WizardCommitResult.Saved)
        assertEquals(1, stores.savedSets.size)
    }

    @Test
    fun heldItemEditsAndAnUnloadedPoolRefusePickedApps() {
        val context = context(entry = WizardEntry.FromSource(WorkspaceSourceIds.HOME_GRID, homeId))
        val finished = finish(context, *favourites())

        val holding = FakeWizardStores(holdingItemEdits = true)
        assertEquals(
            WizardCommitResult.Refused(WizardText.HOLDING_EDITS),
            WizardCommit.commit(finished, context, holding, placement),
        )
        assertTrue(holding.savedSets.isEmpty() && holding.savedPools.isEmpty())

        val unloaded = FakeWizardStores(pool = null)
        assertEquals(
            WizardCommitResult.Refused(WizardText.POOL_NOT_LOADED),
            WizardCommit.commit(finished, context, unloaded, placement),
        )
        assertTrue(unloaded.savedSets.isEmpty())
    }

    @Test
    fun workspacesNotLoadedOrDeletedMeanwhileAreRefused() {
        val context = context(entry = WizardEntry.FromSource(SourceIds.NOTIFICATIONS, homeId))
        val finished = finish(context)

        val notLoaded = FakeWizardStores(set = null)
        assertEquals(
            WizardCommitResult.Refused(WizardText.NOT_LOADED),
            WizardCommit.commit(finished, context, notLoaded, placement),
        )

        // The workspace was replaced by another one while the wizard was open: validated against what is stored now.
        val other = WizardTestFixtures.home.copy(id = WorkspaceId("other"))
        val changed =
            FakeWizardStores(
                set =
                    WizardTestFixtures.set.withLayout(
                        phone,
                        WizardTestFixtures.layout.copy(
                            workspaces = listOf(other),
                            activeId = other.id,
                            defaultId = other.id,
                        ),
                    ),
            )
        val refused = WizardCommit.commit(finished, context, changed, placement)
        assertTrue(refused is WizardCommitResult.Refused)
        assertTrue(changed.savedSets.isEmpty())
    }

    @Test
    fun theEditorGetsOneEditAndTheLibraryInsteadOfAWrite() {
        val stores = FakeWizardStores()
        val context = context()
        val finished = finish(context, WizardAction.ChooseGoal(ContentGoalId.MY_APPS))

        val result = WizardCommit.forEditor(finished, context, stores, placement) as EditorWizardResult.Apply

        assertTrue(result.edit is WorkspaceEdit.AddPage)
        val lens = result.library.lenses.single()
        assertEquals("My apps, grid", lens.name)
        val page = ((result.edit as WorkspaceEdit.AddPage).page as PageContainer).content as PageContent.Bound
        assertEquals(lens.id, page.binding.ref)
        assertTrue(stores.savedSets.isEmpty())
        assertNotNull(result.name)
    }

    @Test
    fun theEditorRefusesPickedAppsWithoutAPool() {
        val context = context()
        val done = finish(context, *favourites())

        assertEquals(
            EditorWizardResult.Refused(WizardText.POOL_NOT_LOADED),
            WizardCommit.forEditor(done, context, null, placement),
        )
    }

    private companion object {
        const val MAX_STEPS = 8
    }
}
