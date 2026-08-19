package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalog
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.home.LauncherTemplateId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.seedHomeLayout

internal fun LauncherShellState.withHomeLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    currentLayoutSet(homeLayoutRepository)
        .withActiveLayout(layout)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { layoutSet ->
            copy(
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
            )
        }

/**
 * Choose which of the per-mode layouts applies.
 *
 * A mode is not a field of a layout. Every mode has a layout of its own, with its own pages and its
 * own dock, and choosing one moves the selection between them -- the layout on screen is saved
 * where it belongs and left there, never written into the mode being switched to.
 *
 * The choice belongs to whichever device class is being configured, which is not always the one
 * being held: settings can be pointed at another device's layout, and choosing a mode there records
 * the preference for that device without changing what is on screen.
 */
internal fun LauncherShellState.withSelectedHomeLayoutMode(
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState {
    val targetDeviceClass = settingsLayoutDeviceClass
    val resolvedMode = viewModeAvailability.availableModeOrStandard(targetDeviceClass, mode)
    val layoutSet =
        currentLayoutSet(homeLayoutRepository)
            .withActiveLayout(homeLayout)
            .withPreferredMode(deviceClass = targetDeviceClass, mode = resolvedMode)
            .let { layouts ->
                // Only the device being held decides what is on screen. A mode chosen for another
                // device class is recorded as its preference and applies when that device is next
                // the active one.
                if (layouts.activeKey.deviceClass == targetDeviceClass) {
                    layouts.selectMode(resolvedMode)
                } else {
                    layouts
                }
            }
            .also(homeLayoutRepository::saveHomeLayoutSet)

    return copy(homeLayout = layoutSet.activeLayout, homeLayoutSet = layoutSet)
}

/**
 * Leave Cards for wherever it was entered from.
 *
 * The destination is the active device's last non-Cards mode, which the layout set remembers;
 * [withSelectedHomeLayoutMode] then makes the switch, so availability and per-mode layouts are
 * handled exactly as any other mode change. Exit is only ever dispatched from the home screen, so
 * the device being configured and the device being held are the same one.
 */
internal fun LauncherShellState.withExitedAdaptiveStage(
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState =
    withSelectedHomeLayoutMode(
        mode = currentLayoutSet(homeLayoutRepository).withActiveLayout(homeLayout).modeLeavingCards(),
        homeLayoutRepository = homeLayoutRepository,
        viewModeAvailability = viewModeAvailability,
    )

internal fun LauncherShellState.withSelectedHomeLayoutTemplate(
    templateId: LauncherTemplateId,
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
    templateCatalog: LauncherTemplateCatalog = LauncherTemplateCatalogDefaults.catalog,
): LauncherShellState {
    val targetDeviceClass = settingsLayoutDeviceClass
    val targetKey = HomeLayoutKey(viewMode = mode, deviceClass = targetDeviceClass)
    val layout =
        if (mode in viewModeAvailability.availableModes(targetDeviceClass)) {
            templateCatalog.templates
                .firstOrNull { template -> template.id == templateId }
                ?.seedHomeLayout(targetKey)
        } else {
            null
        }

    return layout?.let { selectedLayout ->
        val currentLayoutSet = currentLayoutSet(homeLayoutRepository).withActiveLayout(homeLayout)
        val updatedLayoutSet =
            currentLayoutSet
                .withLayout(key = targetKey, layout = selectedLayout)
                .withPreferredMode(deviceClass = targetDeviceClass, mode = mode)
                .let { layoutSet ->
                    if (layoutSet.activeKey.deviceClass == targetDeviceClass) {
                        layoutSet.selectMode(mode)
                    } else {
                        layoutSet
                    }
                }

        homeLayoutRepository.saveHomeLayoutSet(updatedLayoutSet)

        copy(
            homeLayout =
                if (updatedLayoutSet.activeKey == targetKey) {
                    updatedLayoutSet.activeLayout
                } else {
                    homeLayout
                },
            homeLayoutSet = updatedLayoutSet,
        )
    } ?: this
}

internal fun LauncherShellState.withSelectedHomeLayoutDeviceClass(
    deviceClass: HomeLayoutDeviceClass,
    availableDeviceClasses: Set<HomeLayoutDeviceClass> = setOf(deviceClass),
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState {
    val layoutSet = currentLayoutSet(homeLayoutRepository)
    val updatedAvailableDeviceClasses = availableLayoutDeviceClasses + availableDeviceClasses + deviceClass

    if (layoutSet.activeKey.deviceClass == deviceClass && layoutSet.activeLayout == homeLayout) {
        return copy(
            availableLayoutDeviceClasses = updatedAvailableDeviceClasses,
            settingsLayoutDeviceClass =
                settingsLayoutDeviceClassForDeviceSelection(
                    layoutSet = layoutSet,
                    availableDeviceClasses = updatedAvailableDeviceClasses,
                ),
        )
    }

    return layoutSet
        .withActiveLayout(homeLayout)
        .selectDeviceClass(deviceClass, viewModeAvailability)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { updatedLayoutSet ->
            copy(
                homeLayout = updatedLayoutSet.activeLayout,
                homeLayoutSet = updatedLayoutSet,
                availableLayoutDeviceClasses = updatedAvailableDeviceClasses,
                settingsLayoutDeviceClass =
                    settingsLayoutDeviceClassForDeviceSelection(
                        layoutSet = updatedLayoutSet,
                        availableDeviceClasses = updatedAvailableDeviceClasses,
                    ),
            )
        }
}

private fun LauncherShellState.settingsLayoutDeviceClassForDeviceSelection(
    layoutSet: HomeLayoutSet,
    availableDeviceClasses: Set<HomeLayoutDeviceClass>,
): HomeLayoutDeviceClass =
    when (destination) {
        ShellDestination.SETTINGS ->
            settingsLayoutDeviceClass
                .takeIf { selectedDeviceClass -> selectedDeviceClass in availableDeviceClasses }
                ?: layoutSet.activeKey.deviceClass

        else -> layoutSet.activeKey.deviceClass
    }

internal fun LauncherShellState.withSettingsLayoutDeviceClass(deviceClass: HomeLayoutDeviceClass): LauncherShellState {
    val supportsSettingsDeviceClass =
        deviceClass in availableLayoutDeviceClasses ||
            deviceClass == HomeLayoutDeviceClass.PHONE ||
            deviceClass == HomeLayoutDeviceClass.PHONE_LANDSCAPE ||
            deviceClass == HomeLayoutDeviceClass.FOLDABLE ||
            deviceClass == HomeLayoutDeviceClass.DESKTOP

    return if (supportsSettingsDeviceClass) {
        copy(
            settingsLayoutDeviceClass = deviceClass,
            availableLayoutDeviceClasses = availableLayoutDeviceClasses + deviceClass,
        )
    } else {
        this
    }
}

internal fun LauncherShellState.withSettingsTargetLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState {
    val key =
        HomeLayoutKey(
            viewMode = layout.viewMode,
            deviceClass = settingsLayoutDeviceClass,
        )
    val currentLayoutSet =
        currentLayoutSet(homeLayoutRepository)
            .withLayout(key = key, layout = layout)
            .withPreferredMode(
                deviceClass = settingsLayoutDeviceClass,
                mode = key.viewMode,
            )
    val layoutSet =
        if (currentLayoutSet.activeKey.deviceClass == settingsLayoutDeviceClass) {
            currentLayoutSet.selectMode(key.viewMode)
        } else {
            currentLayoutSet
        }

    homeLayoutRepository.saveHomeLayoutSet(layoutSet)

    return copy(
        homeLayout = layoutSet.activeLayout,
        homeLayoutSet = layoutSet,
    )
}

internal val LauncherShellState.settingsTargetLayoutKey: HomeLayoutKey
    get() =
        homeLayoutSet.activeKey.takeIf { key -> key.deviceClass == settingsLayoutDeviceClass }
            ?: HomeLayoutKey(
                viewMode = homeLayoutSet.preferredModesByDeviceClass[settingsLayoutDeviceClass] ?: homeLayout.viewMode,
                deviceClass = settingsLayoutDeviceClass,
            )

internal fun LauncherShellState.settingsTargetLayout(homeLayoutRepository: HomeLayoutRepository): HomeLayout =
    currentLayoutSet(homeLayoutRepository).layoutFor(settingsTargetLayoutKey)

private fun LauncherShellState.currentLayoutSet(homeLayoutRepository: HomeLayoutRepository): HomeLayoutSet =
    homeLayoutRepository.loadHomeLayoutSet() ?: homeLayoutSet.withActiveLayout(homeLayout)
