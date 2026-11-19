package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal class LauncherWidgetPickerActionReducer {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState? =
        when (action) {
            LauncherShellAction.OpenWidgetPicker,
            LauncherShellAction.OpenWidgetPickerForDockPanel,
            LauncherShellAction.CloseWidgetPicker,
            is LauncherShellAction.OpenWidgetPickerForStack,
            is LauncherShellAction.OpenWidgetStackEditor,
            LauncherShellAction.CloseWidgetStackEditor,
            is LauncherShellAction.ConfirmRemoveWidgetStack,
            LauncherShellAction.DismissRemoveWidgetStack,
            -> state.withWidgetPickerAction(action)

            else -> null
        }
}
