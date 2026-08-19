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
    fun opensWidgetPickerAimedAtTheDockPanel() {
        val updated = reducer.reduce(LauncherShellState(), LauncherShellAction.OpenWidgetPickerForDockPanel)

        assertEquals(true, updated?.isWidgetPickerOpen)
        assertEquals(true, updated?.isWidgetPickerTargetingDockPanel)
    }

    @Test
    fun openingTheWidgetPickerNormallyClearsAnEarlierPanelAim() {
        val updated =
            reducer.reduce(
                LauncherShellState(isWidgetPickerOpen = false, isWidgetPickerTargetingDockPanel = true),
                LauncherShellAction.OpenWidgetPicker,
            )

        assertEquals(false, updated?.isWidgetPickerTargetingDockPanel)
    }

    @Test
    fun closesWidgetPicker() {
        val updated =
            reducer.reduce(
                LauncherShellState(isWidgetPickerOpen = true, isWidgetPickerTargetingDockPanel = true),
                LauncherShellAction.CloseWidgetPicker,
            )

        assertEquals(false, updated?.isWidgetPickerOpen)
        assertEquals(false, updated?.isWidgetPickerTargetingDockPanel)
    }

    @Test
    fun ignoresNonWidgetPickerActions() {
        assertNull(reducer.reduce(LauncherShellState(), LauncherShellAction.OpenSettings))
    }
}
