package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.MAX_HOME_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_LINES
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_MAX_WIDTH_DP
import com.riffle.core.domain.launcher.home.MAX_HOME_LABEL_TEXT_SIZE_SP
import com.riffle.core.domain.launcher.home.MIN_HOME_ICON_SIZE_DP
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
        HomeIconSizeSetting(settings = settings, onAction = onAction)
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
private fun HomeIconSizeSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "App icon size",
    value = settings.iconSizeDp,
    valueRange = MIN_HOME_ICON_SIZE_DP..MAX_HOME_ICON_SIZE_DP,
    valueLabel = { "$it dp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectHomeIconSize(value)) },
)

@Composable
private fun HomeLabelVisibilitySetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSwitchRow(
        title = "Show labels",
        subtitle = if (settings.showText) "Labels visible on home" else "Icon-only home",
        checked = settings.showText,
        onCheckedChange = { value -> onAction(LauncherShellAction.SelectHomeLabelTextVisible(value)) },
    )
}

@Composable
private fun HomeLabelBackgroundSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Label background",
    value = settings.backgroundAlphaPercent,
    valueRange = MIN_HOME_LABEL_BACKGROUND_ALPHA_PERCENT..MAX_HOME_LABEL_BACKGROUND_ALPHA_PERCENT,
    valueLabel = { "$it%" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectHomeLabelBackgroundAlpha(value)) },
)

@Composable
private fun HomeLabelTextSizeSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Label text size",
    value = settings.textSizeSp,
    valueRange = MIN_HOME_LABEL_TEXT_SIZE_SP..MAX_HOME_LABEL_TEXT_SIZE_SP,
    valueLabel = { "$it sp" },
    onValueChange = { value -> onAction(LauncherShellAction.SelectHomeLabelTextSize(value)) },
)

@Composable
private fun HomeLabelWidthSetting(
    settings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) = DiscreteSettingSlider(
    title = "Label width",
    value = settings.maxWidthDp,
    valueRange = MIN_HOME_LABEL_MAX_WIDTH_DP..MAX_HOME_LABEL_MAX_WIDTH_DP,
    valueLabel = { homeLabelWidthDescription(settings.copy(maxWidthDp = it)) },
    onValueChange = { value -> onAction(LauncherShellAction.SelectHomeLabelMaxWidth(value)) },
)

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
) = DiscreteSettingSlider(
    title = "Label lines",
    value = settings.maxLines,
    valueRange = MIN_HOME_LABEL_MAX_LINES..MAX_HOME_LABEL_MAX_LINES,
    valueLabel = Int::toString,
    onValueChange = { value -> onAction(LauncherShellAction.SelectHomeLabelMaxLines(value)) },
)

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
