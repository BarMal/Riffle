package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class SettledLayoutMemoTest {
    @Test
    fun skipsTheTransformWhileLayoutAndInputsAreUnchanged() {
        val memo = SettledLayoutMemo<List<String>>()
        val layout = HomeLayoutDefaults.standard()
        var runs = 0
        val identity: (HomeLayout) -> HomeLayout = { input -> input.also { runs += 1 } }

        memo.transformUnlessSettled(layout, listOf("camera"), identity)
        memo.transformUnlessSettled(layout, listOf("camera"), identity)
        memo.transformUnlessSettled(layout.copy(), listOf("camera"), identity)

        assertEquals(1, runs)
    }

    @Test
    fun rerunsWhenTheInputsChange() {
        val memo = SettledLayoutMemo<List<String>>()
        val layout = HomeLayoutDefaults.standard()
        var runs = 0
        val identity: (HomeLayout) -> HomeLayout = { input -> input.also { runs += 1 } }

        memo.transformUnlessSettled(layout, listOf("camera"), identity)
        memo.transformUnlessSettled(layout, listOf("camera", "music"), identity)

        assertEquals(2, runs)
    }

    @Test
    fun rerunsWhenTheGridChanges() {
        val memo = SettledLayoutMemo<List<String>>()
        val layout = HomeLayoutDefaults.standard()
        val regridded =
            layout.copy(
                settings =
                    layout.settings.copy(
                        grid = layout.settings.grid.copy(dimensions = GridDimensions(columns = 3, rows = 3)),
                    ),
            )
        var runs = 0
        val identity: (HomeLayout) -> HomeLayout = { input -> input.also { runs += 1 } }

        memo.transformUnlessSettled(layout, listOf("camera"), identity)
        memo.transformUnlessSettled(regridded, listOf("camera"), identity)

        assertEquals(2, runs)
    }

    @Test
    fun neverRemembersAResultThatChangedTheLayout() {
        val memo = SettledLayoutMemo<List<String>>()
        val layout = HomeLayoutDefaults.standard()
        val changed = layout.copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY)
        var runs = 0
        val change: (HomeLayout) -> HomeLayout = { _ -> changed.also { runs += 1 } }

        assertEquals(changed, memo.transformUnlessSettled(layout, emptyList(), change))
        assertEquals(changed, memo.transformUnlessSettled(layout, emptyList(), change))
        assertEquals(2, runs)
    }
}
