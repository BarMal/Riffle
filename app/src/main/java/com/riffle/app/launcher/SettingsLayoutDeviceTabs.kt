package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsLayoutDeviceTabs(
    selectedDeviceClass: HomeLayoutDeviceClass,
    availableDeviceClasses: Set<HomeLayoutDeviceClass>,
    onAction: (LauncherShellAction) -> Unit,
) {
    val tabs = settingsLayoutDeviceTabs(availableDeviceClasses)

    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab.deviceClass == selectedDeviceClass,
                onClick = { onAction(LauncherShellAction.SelectSettingsLayoutDeviceClass(tab.deviceClass)) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                label = {
                    Text(
                        text = tab.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

internal data class SettingsLayoutDeviceTab(
    val label: String,
    val deviceClass: HomeLayoutDeviceClass,
)

internal fun settingsLayoutDeviceTabs(classes: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> {
    val hasTabletOnly = classes == setOf(HomeLayoutDeviceClass.TABLET)
    if (hasTabletOnly) {
        return listOf(
            SettingsLayoutDeviceTab(
                label = "Tablet",
                deviceClass = HomeLayoutDeviceClass.TABLET,
            ),
        )
    }
    return listOf(
        HomeLayoutDeviceClass.PHONE,
        HomeLayoutDeviceClass.FOLDABLE,
    )
        .map { deviceClass ->
            SettingsLayoutDeviceTab(
                label = deviceClass.settingsLabel(),
                deviceClass = deviceClass,
            )
        }
}

private fun HomeLayoutDeviceClass.settingsLabel(): String =
    when (this) {
        HomeLayoutDeviceClass.PHONE -> "Folded"
        HomeLayoutDeviceClass.FOLDABLE -> "Unfolded"
        HomeLayoutDeviceClass.TABLET -> "Tablet"
    }
