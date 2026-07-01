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
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsLayoutDeviceButton(
            label = "Unfolded",
            deviceClass = HomeLayoutDeviceClass.PHONE,
            selectedDeviceClass = selectedDeviceClass,
            onAction = onAction,
        )
        SettingsLayoutDeviceButton(
            label = "Folded",
            deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            selectedDeviceClass = selectedDeviceClass,
            onAction = onAction,
        )
        SettingsLayoutDeviceButton(
            label = "Tablet",
            deviceClass = HomeLayoutDeviceClass.TABLET,
            selectedDeviceClass = selectedDeviceClass,
            onAction = onAction,
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
