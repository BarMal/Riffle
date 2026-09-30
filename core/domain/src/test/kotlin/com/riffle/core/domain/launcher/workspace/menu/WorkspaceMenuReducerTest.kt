package com.riffle.core.domain.launcher.workspace.menu

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceMenuReducerTest {
    private val reducer = WorkspaceMenuReducer()
    private val open = WorkspaceMenuState(isOpen = true)
    private val page = WorkspacePageKey.Page(ContainerId("p"))

    private fun model(finder: Boolean = true) =
        WorkspaceMenuModel(
            switchEntries =
                listOf(
                    WorkspaceSwitchEntry(WorkspaceId("a"), "A", isActive = true, isDisplayed = true),
                    WorkspaceSwitchEntry(WorkspaceId("b"), "B", isActive = false, isDisplayed = false),
                ),
            jumpEntries = listOf(WorkspaceJumpEntry(page, 1)),
            omittedGroupCount = 0,
            finder = if (finder) WorkspaceFinderEntry(ContainerId("f")) else null,
            editTarget = WorkspaceId("a"),
            fallback = null,
        )

    @Test
    fun opensAndCloses() {
        assertTrue(reducer.reduce(WorkspaceMenuState(), WorkspaceMenuAction.Open, model()).state.isOpen)
        assertFalse(reducer.reduce(open, WorkspaceMenuAction.Close, model()).state.isOpen)
    }

    @Test
    fun cannotOpenWithoutAModelAndClosesIfTheModelVanishes() {
        assertFalse(reducer.reduce(WorkspaceMenuState(), WorkspaceMenuAction.Open, null).state.isOpen)
        assertFalse(reducer.reduce(open, WorkspaceMenuAction.OpenFinder, null).state.isOpen)
    }

    @Test
    fun switchingToAnotherWorkspaceEmitsTheEffectAndCloses() {
        val result = reducer.reduce(open, WorkspaceMenuAction.SwitchWorkspace(WorkspaceId("b")), model())
        assertEquals(WorkspaceMenuEffect.SetActiveWorkspace(WorkspaceId("b")), result.effect)
        assertFalse(result.state.isOpen)
    }

    @Test
    fun reselectingTheActiveWorkspaceOnlyCloses() {
        val result = reducer.reduce(open, WorkspaceMenuAction.SwitchWorkspace(WorkspaceId("a")), model())
        assertNull(result.effect)
        assertFalse(result.state.isOpen)
    }

    @Test
    fun unknownWorkspaceOrPageIsIgnoredAndStaysOpen() {
        val unknownWorkspace = reducer.reduce(open, WorkspaceMenuAction.SwitchWorkspace(WorkspaceId("zz")), model())
        assertNull(unknownWorkspace.effect)
        assertTrue(unknownWorkspace.state.isOpen)
        val unknownPage =
            reducer.reduce(open, WorkspaceMenuAction.JumpToPage(WorkspacePageKey.Page(ContainerId("x"))), model())
        assertNull(unknownPage.effect)
        assertTrue(unknownPage.state.isOpen)
    }

    @Test
    fun jumpFinderAndEditEmitEffects() {
        assertEquals(
            WorkspaceMenuEffect.NavigateToPage(page),
            reducer.reduce(open, WorkspaceMenuAction.JumpToPage(page), model()).effect,
        )
        assertEquals(
            WorkspaceMenuEffect.OpenFinderPage(ContainerId("f")),
            reducer.reduce(open, WorkspaceMenuAction.OpenFinder, model()).effect,
        )
        val edit = reducer.reduce(open, WorkspaceMenuAction.EditWorkspace, model())
        assertEquals(WorkspaceMenuEffect.EditWorkspace(WorkspaceId("a")), edit.effect)
        assertFalse(edit.state.isOpen)
    }

    @Test
    fun finderWithoutAFinderPageIsIgnored() {
        val result = reducer.reduce(open, WorkspaceMenuAction.OpenFinder, model(finder = false))
        assertNull(result.effect)
        assertTrue(result.state.isOpen)
    }

    @Test
    fun selectionsWhileClosedDoNothing() {
        val result = reducer.reduce(WorkspaceMenuState(), WorkspaceMenuAction.EditWorkspace, model())
        assertNull(result.effect)
        assertFalse(result.state.isOpen)
    }
}
