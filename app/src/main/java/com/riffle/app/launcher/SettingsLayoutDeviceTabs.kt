package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

@Composable
internal fun SettingsLayoutDeviceTabs(
    selectedDeviceClass: HomeLayoutDeviceClass,
    availableDeviceClasses: Set<HomeLayoutDeviceClass>,
    onAction: (LauncherShellAction) -> Unit,
) {
    val tabs = settingsLayoutDeviceTabs(availableDeviceClasses)
    if (tabs.size <= 1) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            SettingsLayoutDeviceButton(
                label = tab.label,
                deviceClass = tab.deviceClass,
                selectedDeviceClass = selectedDeviceClass,
                onAction = onAction,
            )
        }
    }
}

internal data class SettingsLayoutDeviceTab(
    val label: String,
    val deviceClass: HomeLayoutDeviceClass,
)

internal fun settingsLayoutDeviceTabs(classes: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> {
    val isFoldableDevice = HomeLayoutDeviceClass.FOLDABLE in classes

    return listOf(
        HomeLayoutDeviceClass.PHONE,
        HomeLayoutDeviceClass.FOLDABLE,
        HomeLayoutDeviceClass.TABLET,
    )
        .filter { deviceClass -> deviceClass in classes }
        .map { deviceClass ->
            SettingsLayoutDeviceTab(
                label = deviceClass.settingsLabel(isFoldableDevice),
                deviceClass = deviceClass,
            )
        }
}

@Composable
private fun SettingsLayoutDeviceButton(
    label: String,
    deviceClass: HomeLayoutDeviceClass,
    selectedDeviceClass: HomeLayoutDeviceClass,
    onAction: (LauncherShellAction) -> Unit,
) {
    TextButton(
        enabled = deviceClass != selectedDeviceClass,
        onClick = { onAction(LauncherShellAction.SelectSettingsLayoutDeviceClass(deviceClass)) },
    ) {
        SettingsButtonText(text = label)
    }
}

private fun HomeLayoutDeviceClass.settingsLabel(isFoldableDevice: Boolean): String =
    when (this) {
        HomeLayoutDeviceClass.PHONE -> if (isFoldableDevice) "Folded" else "Phone"
        HomeLayoutDeviceClass.FOLDABLE -> "Unfolded"
        HomeLayoutDeviceClass.TABLET -> "Tablet"
    }
