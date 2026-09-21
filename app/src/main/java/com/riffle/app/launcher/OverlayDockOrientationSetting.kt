package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation

@Composable
internal fun OverlayDockExpandedOrientationSetting(
    orientation: OverlayDockExpandedOrientation,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsChoiceRow(
        title = "Expanded orientation",
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
