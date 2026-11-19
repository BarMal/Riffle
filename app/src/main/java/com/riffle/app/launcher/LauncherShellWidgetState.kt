package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState

internal fun LauncherShellState.withWidgetPickerAction(action: LauncherShellAction): LauncherShellState =
    when (action) {
        LauncherShellAction.OpenWidgetPicker ->
            copy(isWidgetPickerOpen = true, isWidgetPickerTargetingDockPanel = false)

        LauncherShellAction.OpenWidgetPickerForDockPanel ->
            copy(isWidgetPickerOpen = true, isWidgetPickerTargetingDockPanel = true)

        LauncherShellAction.CloseWidgetPicker ->
            copy(isWidgetPickerOpen = false, isWidgetPickerTargetingDockPanel = false, widgetPickerStackItemId = null)

        is LauncherShellAction.OpenWidgetPickerForStack ->
            copy(
                isWidgetPickerOpen = true,
                isWidgetPickerTargetingDockPanel = false,
                widgetPickerStackItemId = action.itemId,
            )

        is LauncherShellAction.OpenWidgetStackEditor -> copy(widgetStackEditorItemId = action.itemId)

        LauncherShellAction.CloseWidgetStackEditor -> copy(widgetStackEditorItemId = null)

        is LauncherShellAction.ConfirmRemoveWidgetStack -> copy(widgetStackRemovalItemId = action.itemId)

        LauncherShellAction.DismissRemoveWidgetStack -> copy(widgetStackRemovalItemId = null)

        else -> this
    }
