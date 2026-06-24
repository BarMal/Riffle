package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.widgets.WidgetProviderCatalog

internal fun LauncherShellState.withWidgetPickerAction(
    action: LauncherShellAction,
    platformDependencies: LauncherShellPlatformDependencies,
    widgetProviderCatalog: WidgetProviderCatalog,
): LauncherShellState =
    when (action) {
        LauncherShellAction.OpenWidgetPicker ->
            copy(
                installedWidgetProviders =
                    platformDependencies.installedWidgetProviders(widgetProviderCatalog),
                isWidgetPickerOpen = true,
            )

        LauncherShellAction.CloseWidgetPicker ->
            copy(isWidgetPickerOpen = false)

        else -> this
    }
