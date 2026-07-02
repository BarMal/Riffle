package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation

@Composable
internal fun OverlayDockExpandedOrientationSetting(
    orientation: OverlayDockExpandedOrientation,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Expanded orientation",
            subtitle = orientation.label,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = orientation != OverlayDockExpandedOrientation.WIDE,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectOverlayDockExpandedOrientation(
                            OverlayDockExpandedOrientation.WIDE,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "Wide")
            }
            TextButton(
                enabled = orientation != OverlayDockExpandedOrientation.TALL,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectOverlayDockExpandedOrientation(
                            OverlayDockExpandedOrientation.TALL,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "Tall")
            }
        }
    }
}

private val OverlayDockExpandedOrientation.label: String
    get() =
        when (this) {
            OverlayDockExpandedOrientation.WIDE -> "Wide"
            OverlayDockExpandedOrientation.TALL -> "Tall"
        }
