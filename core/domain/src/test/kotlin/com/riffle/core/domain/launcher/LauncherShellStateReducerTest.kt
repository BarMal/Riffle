package com.riffle.core.domain.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherShellStateReducerTest {
    private val reducer = LauncherShellStateReducer()

    @Test
    fun defaultHomeCompletesFirstRun() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            )

        assertEquals(FirstRunStatus.COMPLETE, state.firstRunStatus)
        assertFalse(state.shouldShowDefaultHomePrompt)
        assertTrue(state.shouldShowEmptyHome)
    }

    @Test
    fun missingDefaultHomeKeepsPromptVisible() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(),
                homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
            )

        assertEquals(FirstRunStatus.NEEDS_HOME_ROLE, state.firstRunStatus)
        assertTrue(state.shouldShowDefaultHomePrompt)
        assertFalse(state.shouldShowEmptyHome)
    }

    @Test
    fun completedFirstRunStaysCompleteWhenRoleCannotBeDetermined() {
        val state =
            reducer.homeRoleChanged(
                currentState = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
                homeRoleStatus = HomeRoleStatus.UNKNOWN,
            )

        assertEquals(FirstRunStatus.COMPLETE, state.firstRunStatus)
        assertTrue(state.shouldShowEmptyHome)
    }
}
