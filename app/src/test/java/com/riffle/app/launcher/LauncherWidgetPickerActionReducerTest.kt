package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherWidgetPickerActionReducerTest {
    private val reducer = LauncherWidgetPickerActionReducer()

    @Test
    fun opensWidgetPicker() {
        val updated = reducer.reduce(LauncherShellState(), LauncherShellAction.OpenWidgetPicker)

        assertEquals(true, updated?.isWidgetPickerOpen)
    }

    @Test
    fun closesWidgetPicker() {
        val updated =
            reducer.reduce(
                LauncherShellState(isWidgetPickerOpen = true),
                LauncherShellAction.CloseWidgetPicker,
            )

        assertEquals(false, updated?.isWidgetPickerOpen)
    }

    @Test
    fun ignoresNonWidgetPickerActions() {
        assertNull(reducer.reduce(LauncherShellState(), LauncherShellAction.OpenSettings))
    }
}
