package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal fun LauncherShellState.withWidgetPickerAction(action: LauncherShellAction): LauncherShellState =
    when (action) {
        LauncherShellAction.OpenWidgetPicker ->
            copy(isWidgetPickerOpen = true, isWidgetPickerTargetingDockPanel = false)

        LauncherShellAction.OpenWidgetPickerForDockPanel ->
            copy(isWidgetPickerOpen = true, isWidgetPickerTargetingDockPanel = true)

        LauncherShellAction.CloseWidgetPicker ->
            copy(isWidgetPickerOpen = false, isWidgetPickerTargetingDockPanel = false)

        else -> this
    }
