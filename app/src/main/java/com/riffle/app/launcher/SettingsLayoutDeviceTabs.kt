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
import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutClasses

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
    val hasDesktopOnly = classes == setOf(HomeLayoutDeviceClass.DESKTOP)
    return when {
        hasTabletOnly ->
            listOf(
                SettingsLayoutDeviceTab(label = "Tablet", deviceClass = HomeLayoutDeviceClass.TABLET),
            )

        hasDesktopOnly ->
            listOf(
                SettingsLayoutDeviceTab(label = "Desktop", deviceClass = HomeLayoutDeviceClass.DESKTOP),
            )
        else ->
            buildList {
                add(HomeLayoutDeviceClass.PHONE)
                if (HomeLayoutDeviceClass.PHONE_LANDSCAPE in classes) add(HomeLayoutDeviceClass.PHONE_LANDSCAPE)
                add(HomeLayoutDeviceClass.FOLDABLE)
                if (HomeLayoutDeviceClass.TABLET in classes) add(HomeLayoutDeviceClass.TABLET)
                if (HomeLayoutDeviceClass.DESKTOP in classes) add(HomeLayoutDeviceClass.DESKTOP)
            }.map { deviceClass ->
                SettingsLayoutDeviceTab(
                    label = deviceClass.settingsLabel(),
                    deviceClass = deviceClass,
                )
            }
    }
}

/**
 * The layout tabs of the Workspaces pages (workspaces, saved lenses, exclusions): no separate landscape tab, as a
 * landscape phone is the phone layout for every Workspaces value (N10). The classic layout pages keep theirs.
 */
internal fun workspaceSettingsLayoutDeviceTabs(classes: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> =
    settingsLayoutDeviceTabs(WorkspaceLayoutClasses.of(classes))

private fun HomeLayoutDeviceClass.settingsLabel(): String =
    when (this) {
        HomeLayoutDeviceClass.PHONE -> "Folded"
        HomeLayoutDeviceClass.PHONE_LANDSCAPE -> "Landscape"
        HomeLayoutDeviceClass.FOLDABLE -> "Unfolded"
        HomeLayoutDeviceClass.TABLET -> "Tablet"
        HomeLayoutDeviceClass.DESKTOP -> "Desktop"
    }
