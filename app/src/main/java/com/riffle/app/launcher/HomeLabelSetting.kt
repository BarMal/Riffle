package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT

@Composable
internal fun HomeLabelSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Label background",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${settings.backgroundAlphaPercent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = settings.backgroundAlphaPercent > MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelBackgroundAlpha(
                            settings.backgroundAlphaPercent - HOME_LABEL_BACKGROUND_ALPHA_STEP_PERCENT,
                        ),
                    )
                },
            ) {
                Text(text = "-")
            }
            TextButton(
                enabled = settings.backgroundAlphaPercent < MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelBackgroundAlpha(
                            settings.backgroundAlphaPercent + HOME_LABEL_BACKGROUND_ALPHA_STEP_PERCENT,
                        ),
                    )
                },
            ) {
                Text(text = "+")
            }
        }
    }
}

private const val HOME_LABEL_BACKGROUND_ALPHA_STEP_PERCENT = 5
