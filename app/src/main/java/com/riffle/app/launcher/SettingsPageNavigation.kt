package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

internal data class SettingsPageNavigation(
    val page: SettingsPage,
    val action: LauncherShellAction,
)

internal fun LauncherShellAction.settingsPageNavigation(): SettingsPageNavigation? =
    when (this) {
        LauncherShellAction.OpenSettings ->
            SettingsPageNavigation(
                page = SettingsPage.MAIN,
                action = LauncherShellAction.OpenSettings,
            )

        is LauncherShellAction.OpenSettingsPage ->
            SettingsPageNavigation(
                page = page,
                action = LauncherShellAction.OpenSettings,
            )

        else -> null
    }

internal data class SettingsPageActionRouter(
    val initialSettingsPage: SettingsPage,
    val onAction: (LauncherShellAction) -> Unit,
)

@Composable
internal fun rememberSettingsPageActionRouter(onAction: (LauncherShellAction) -> Unit): SettingsPageActionRouter {
    val initialSettingsPage = remember { mutableStateOf(SettingsPage.MAIN) }
    val routedOnAction: (LauncherShellAction) -> Unit = { action ->
        when (val navigation = action.settingsPageNavigation()) {
            null -> onAction(action)
            else -> {
                initialSettingsPage.value = navigation.page
                onAction(navigation.action)
            }
        }
    }

    return SettingsPageActionRouter(
        initialSettingsPage = initialSettingsPage.value,
        onAction = routedOnAction,
    )
}
