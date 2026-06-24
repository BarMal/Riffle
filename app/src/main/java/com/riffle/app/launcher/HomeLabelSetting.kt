package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_LINES
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_WIDTH_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_TEXT_SIZE_SP
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_MAX_LINES
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_MAX_WIDTH_DP
import com.riffle.core.domain.launcher.home.MIN_HOME_LABEL_TEXT_SIZE_SP

@Composable
internal fun HomeLabelSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeLabelVisibilitySetting(
            settings = settings,
            onAction = onAction,
        )
        HomeLabelBackgroundSetting(
            settings = settings,
            onAction = onAction,
        )
        HomeLabelTextSizeSetting(
            settings = settings,
            onAction = onAction,
        )
        HomeLabelWidthSetting(
            settings = settings,
            onAction = onAction,
        )
        HomeLabelSizingSetting(
            settings = settings,
            onAction = onAction,
        )
        HomeLabelLineCountSetting(
            settings = settings,
            onAction = onAction,
        )
    }
}

@Composable
private fun HomeLabelVisibilitySetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Show labels",
            subtitle = if (settings.showText) "Labels visible on home" else "Icon-only home",
        )
        Switch(
            checked = settings.showText,
            onCheckedChange = { value -> onAction(LauncherShellAction.SelectHomeLabelTextVisible(value)) },
        )
    }
}

@Composable
private fun HomeLabelBackgroundSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Label background",
            subtitle = "${settings.backgroundAlphaPercent}%",
        )
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
                SettingsButtonText(text = "-")
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
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val HOME_LABEL_BACKGROUND_ALPHA_STEP_PERCENT = 5

@Composable
private fun HomeLabelTextSizeSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Label text size",
            subtitle = "${settings.textSizeSp} sp",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = settings.textSizeSp > MIN_HOME_LABEL_TEXT_SIZE_SP,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelTextSize(
                            settings.textSizeSp - HOME_LABEL_TEXT_SIZE_STEP_SP,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = settings.textSizeSp < MAX_HOME_LABEL_TEXT_SIZE_SP,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelTextSize(
                            settings.textSizeSp + HOME_LABEL_TEXT_SIZE_STEP_SP,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val HOME_LABEL_TEXT_SIZE_STEP_SP = 1

@Composable
private fun HomeLabelWidthSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Label width",
            subtitle = homeLabelWidthDescription(settings),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = settings.maxWidthDp > MIN_HOME_LABEL_MAX_WIDTH_DP,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelMaxWidth(
                            settings.maxWidthDp - HOME_LABEL_WIDTH_STEP_DP,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = settings.maxWidthDp < MAX_HOME_LABEL_MAX_WIDTH_DP,
                onClick = {
                    onAction(
                        LauncherShellAction.SelectHomeLabelMaxWidth(
                            settings.maxWidthDp + HOME_LABEL_WIDTH_STEP_DP,
                        ),
                    )
                },
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

private const val HOME_LABEL_WIDTH_STEP_DP = 8

internal fun homeLabelWidthDescription(settings: HomeLabelSettings): String =
    when (settings.sizing) {
        HomeLabelSizing.FIXED -> "Fixed ${settings.maxWidthDp} dp"
        HomeLabelSizing.DYNAMIC -> "Up to ${settings.maxWidthDp} dp"
    }

@Composable
private fun HomeLabelSizingSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Label sizing",
            subtitle = settings.sizing.label,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeLabelSizing.values().forEach { sizing ->
                TextButton(
                    enabled = settings.sizing != sizing,
                    onClick = { onAction(LauncherShellAction.SelectHomeLabelSizing(sizing)) },
                ) {
                    SettingsButtonText(text = sizing.buttonLabel)
                }
            }
        }
    }
}

@Composable
private fun HomeLabelLineCountSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextColumn(
            modifier = Modifier.weight(1f),
            title = "Label lines",
            subtitle = settings.maxLines.toString(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = settings.maxLines > MIN_HOME_LABEL_MAX_LINES,
                onClick = { onAction(LauncherShellAction.SelectHomeLabelMaxLines(settings.maxLines - 1)) },
            ) {
                SettingsButtonText(text = "-")
            }
            TextButton(
                enabled = settings.maxLines < MAX_HOME_LABEL_MAX_LINES,
                onClick = { onAction(LauncherShellAction.SelectHomeLabelMaxLines(settings.maxLines + 1)) },
            ) {
                SettingsButtonText(text = "+")
            }
        }
    }
}

private val HomeLabelSizing.buttonLabel: String
    get() =
        when (this) {
            HomeLabelSizing.FIXED -> "Fixed"
            HomeLabelSizing.DYNAMIC -> "Dynamic"
        }

private val HomeLabelSizing.label: String
    get() =
        when (this) {
            HomeLabelSizing.FIXED -> "Fixed-width label backgrounds"
            HomeLabelSizing.DYNAMIC -> "Resize backgrounds to label text"
        }
