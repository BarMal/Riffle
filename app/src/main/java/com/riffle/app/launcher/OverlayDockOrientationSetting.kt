package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation

@Composable
internal fun OverlayDockExpandedOrientationSetting(
    orientation: OverlayDockExpandedOrientation,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.FLOATING_ORIENTATION),
        title = SettingKey.FLOATING_ORIENTATION.label(),
        subtitle = orientation.label,
        options = OverlayDockExpandedOrientation.entries,
        selected = orientation,
        onSelect = { candidate -> onAction(LauncherShellAction.SelectOverlayDockExpandedOrientation(candidate)) },
        label = { candidate -> candidate.label },
    )
}

private val OverlayDockExpandedOrientation.label: String
    get() =
        when (this) {
            OverlayDockExpandedOrientation.WIDE -> "Wide"
            OverlayDockExpandedOrientation.TALL -> "Tall"
        }
