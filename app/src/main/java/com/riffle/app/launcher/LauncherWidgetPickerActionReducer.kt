package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal class LauncherWidgetPickerActionReducer {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState? =
        when (action) {
            LauncherShellAction.OpenWidgetPicker,
            LauncherShellAction.CloseWidgetPicker,
            -> state.withWidgetPickerAction(action)

            else -> null
        }
}
