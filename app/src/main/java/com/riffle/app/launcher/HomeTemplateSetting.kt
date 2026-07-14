@file:Suppress("MatchingDeclarationName")

package com.riffle.app.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalog
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.home.LauncherTemplateId
import com.riffle.core.domain.launcher.home.LauncherViewMode

internal data class HomeTemplateOption(
    val id: LauncherTemplateId,
    val displayName: String,
    val description: String,
    val viewMode: LauncherViewMode,
)

internal fun homeTemplateOptions(
    availableViewModes: List<LauncherViewMode>,
    deviceClass: HomeLayoutDeviceClass,
    catalog: LauncherTemplateCatalog = LauncherTemplateCatalogDefaults.catalog,
): List<HomeTemplateOption> =
    catalog.templates
        .flatMap { template ->
            availableViewModes
                .filter { viewMode -> template.supports(viewMode, deviceClass) }
                .map { viewMode ->
                    HomeTemplateOption(
                        id = template.id,
                        displayName = template.metadata.displayName,
                        description = template.metadata.description,
                        viewMode = viewMode,
                    )
                }
        }.sortedBy { option -> option.displayName }

@Composable
internal fun HomeViewModePresetSetting(
    selectedViewMode: LauncherViewMode,
    availableViewModes: List<LauncherViewMode>,
    deviceClass: HomeLayoutDeviceClass,
    onAction: (LauncherShellAction) -> Unit,
) {
    val options = homeTemplateOptions(availableViewModes = availableViewModes, deviceClass = deviceClass)
    val selectedOption = options.firstOrNull { option -> option.viewMode == selectedViewMode }

    Column {
        SettingsTextColumn(
            title = "Launcher mode presets",
            subtitle =
                selectedOption
                    ?.let { option ->
                        "${option.displayName}: switches launcher mode and keeps your current pages."
                    }
                    ?: "No compatible launcher mode preset is available",
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                TextButton(
                    enabled = option.viewMode != selectedViewMode,
                    onClick = { onAction(LauncherShellAction.SelectLauncherViewMode(option.viewMode)) },
                ) {
                    SettingsButtonText(text = option.viewMode.presetLabel)
                }
            }
        }
    }
}

private val LauncherViewMode.presetLabel: String
    get() =
        when (this) {
            LauncherViewMode.STANDARD_APP_DRAWER -> "Standard"
            LauncherViewMode.HOME_SCREEN_LIBRARY -> "Library"
            LauncherViewMode.CARD_INTERFACE -> "Cards"
        }
