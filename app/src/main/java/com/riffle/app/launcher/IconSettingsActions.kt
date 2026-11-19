package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

/** The actions of Settings > Appearance > Icons, and of the per-app icon and name changes. */
internal val LauncherShellAction.isIconSettingsAction: Boolean
    get() =
        when (this) {
            is LauncherShellAction.SelectIconShape,
            is LauncherShellAction.SelectLegacyIconHandling,
            is LauncherShellAction.SetThemedIcons,
            is LauncherShellAction.SetThemeAllIcons,
            is LauncherShellAction.SelectIconPack,
            is LauncherShellAction.SetAppIconOverride,
            is LauncherShellAction.SetAppLabelOverride,
            is LauncherShellAction.RestoreIconOverrides,
            LauncherShellAction.ResetIconChanges,
            -> true

            else -> false
        }

internal fun LauncherShellState.withIconSettingsAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState {
    val icons = launcherSettings.icons
    val changed =
        when (action) {
            is LauncherShellAction.SelectIconShape -> icons.copy(shape = action.shape)
            is LauncherShellAction.SelectLegacyIconHandling -> icons.copy(legacyIcons = action.handling)
            is LauncherShellAction.SetThemedIcons -> icons.copy(themedIcons = action.enabled)
            is LauncherShellAction.SetThemeAllIcons -> icons.copy(themeAllIcons = action.enabled)
            is LauncherShellAction.SelectIconPack -> icons.copy(iconPack = action.packPackage)
            is LauncherShellAction.SetAppIconOverride -> icons.withIconOverride(action.identity, action.icon)
            is LauncherShellAction.SetAppLabelOverride -> icons.withLabelOverride(action.identity, action.label)
            LauncherShellAction.ResetIconChanges -> icons.withoutIconOverrides()
            is LauncherShellAction.RestoreIconOverrides -> icons.copy(overrides = action.overrides)
            else -> return this
        }
    return if (changed == icons) {
        this
    } else {
        withLauncherSettings(
            settings = launcherSettings.copy(icons = changed),
            launcherSettingsRepository = launcherSettingsRepository,
        )
    }
}
