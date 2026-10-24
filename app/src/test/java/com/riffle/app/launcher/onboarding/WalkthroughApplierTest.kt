package com.riffle.app.launcher.onboarding

import com.riffle.app.launcher.onboarding.WalkthroughFixtures.PHONE
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.wizard.OnboardingOrigin
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughContext
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughReducer
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughState
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Applying through the stores: one write each, the home's guards, and Undo with the returned values. */
class WalkthroughApplierTest {
    private val stores = FakeWalkthroughStores()
    private val applier = WalkthroughApplier(stores, WalkthroughFixtures.ids("apply"))

    private fun finished(context: WalkthroughContext): WalkthroughState {
        var state = WalkthroughFlow.start(context)
        repeat(MAX_STEPS) {
            if (state.step != WalkthroughStep.DONE) {
                state = WalkthroughReducer.reduce(state, WalkthroughAction.Next, context)
            }
        }
        assertEquals(WalkthroughStep.DONE, state.step)
        return state
    }

    @Test
    fun aFreshInstallReplacesTheSeededHomeAndUndoPutsItBack() {
        val context = WalkthroughFixtures.context()
        val seeded = stores.set!!
        val pool = stores.pools.getValue(PHONE)
        val outcome = applier.apply(finished(context), context, PHONE) as WalkthroughOutcome.Applied

        val layout = stores.set!!.workspacesFor(PHONE)
        assertEquals(listOf(WalkthroughFlow.WORKSPACE_ID), layout.workspaces.map { it.id })
        assertEquals(WalkthroughFlow.WORKSPACE_ID, layout.activeId)
        assertTrue(WorkspaceHome.hasHomeGrid(layout.active))
        assertEquals(1, stores.workspaceWrites)
        assertEquals(1, stores.poolWrites)
        assertTrue(outcome.poolSaved)
        assertNotEquals(pool, stores.pools.getValue(PHONE))

        assertTrue(applier.undo(outcome))
        assertEquals(seeded, stores.set)
        assertEquals(pool, stores.pools.getValue(PHONE))
    }

    @Test
    fun anExistingUserKeepsEveryWorkspace() {
        val context = WalkthroughFixtures.context(OnboardingOrigin.EXISTING_USER)
        val before = stores.set!!.workspacesFor(PHONE)
        applier.apply(finished(context), context, PHONE) as WalkthroughOutcome.Applied
        val after = stores.set!!.workspacesFor(PHONE)
        before.workspaces.forEach { assertEquals(it, after.find(it.id)) }
        assertEquals(WalkthroughFlow.WORKSPACE_ID, after.activeId)
        assertEquals(before.defaultId, after.defaultId)
    }

    @Test
    fun applyingTwiceUpdatesMyHomeInPlace() {
        val context = WalkthroughFixtures.context(OnboardingOrigin.EXISTING_USER, firstRun = false)
        applier.apply(finished(context), context, PHONE)
        val once = stores.set
        applier.apply(finished(context), context, PHONE)
        assertEquals(once, stores.set)
        assertEquals(1, stores.set!!.workspacesFor(PHONE).workspaces.count { it.id == WalkthroughFlow.WORKSPACE_ID })
    }

    @Test
    fun aReadOnlyHomeRefusesAndWritesNothing() {
        stores.readOnly = true
        val context = WalkthroughFixtures.context()
        assertEquals(WalkthroughOutcome.ReadOnly, applier.apply(finished(context), context, PHONE))
        assertEquals(0, stores.workspaceWrites)
        assertEquals(0, stores.poolWrites)
    }

    @Test
    fun editsHeldWhileLoadingWaitAndWriteNothing() {
        stores.holding = true
        val context = WalkthroughFixtures.context()
        assertEquals(WalkthroughOutcome.Loading, applier.apply(finished(context), context, PHONE))
        stores.set = null
        stores.holding = false
        assertEquals(WalkthroughOutcome.Loading, applier.apply(finished(context), context, PHONE))
        assertEquals(0, stores.workspaceWrites + stores.poolWrites)
    }

    @Test
    fun withoutAPoolOnlyTheWorkspacesAreWrittenAndUndone() {
        stores.pools.clear()
        val context = WalkthroughFixtures.context(OnboardingOrigin.EXISTING_USER)
        val before = stores.set
        val outcome = applier.apply(finished(context), context, PHONE) as WalkthroughOutcome.Applied
        assertFalse(outcome.poolSaved)
        assertEquals(0, stores.poolWrites)
        assertTrue(applier.undo(outcome))
        assertEquals(before, stores.set)
        assertEquals(0, stores.poolWrites)
    }

    @Test
    fun undoIsRefusedOnceTheHomeIsReadOnly() {
        val context = WalkthroughFixtures.context()
        val outcome = applier.apply(finished(context), context, PHONE) as WalkthroughOutcome.Applied
        val applied = stores.set
        stores.readOnly = true
        assertFalse(applier.undo(outcome))
        assertEquals(applied, stores.set)
    }

    private companion object {
        const val MAX_STEPS = 40
    }
}
