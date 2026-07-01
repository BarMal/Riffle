package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal fun LauncherShellState.withWidgetPickerAction(action: LauncherShellAction): LauncherShellState =
    when (action) {
        LauncherShellAction.OpenWidgetPicker ->
            copy(isWidgetPickerOpen = true)

        LauncherShellAction.CloseWidgetPicker ->
            copy(isWidgetPickerOpen = false)

        else -> this
    }
