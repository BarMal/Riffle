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
import com.riffle.core.domain.launcher.home.ModePair
import com.riffle.core.domain.launcher.home.seedHomeLayout
import com.riffle.core.domain.launcher.home.withHomeMode
import com.riffle.core.domain.launcher.home.withLayoutKeepingDock
import com.riffle.core.domain.launcher.modeSwitchTargetDeviceClass

/**
 * Replace the layout on screen.
 *
 * The in-memory [LauncherShellState.homeLayoutSet] is the source of truth; the repository is only
 * told about the result (and writes it behind). Nothing here reads the layout set back from storage,
 * so an edit can never be based on a copy that lags what is on screen (#1176, #1198).
 */
internal fun LauncherShellState.withHomeLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    homeLayoutSet
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
 * A mode is not a field of a layout. Every mode has a layout of its own, with its own pages, and
 * choosing one moves the selection between them -- the layout on screen is saved where it belongs
 * and left there, never written into the mode being switched to. The dock is the exception: there is
 * one per device class, shared by every mode, so it is the same before and after the switch (#1205).
 *
 * The choice belongs to [targetDeviceClass]. From Settings that is whichever device class is being
 * configured, which can be another device's layout: choosing a mode there records the preference for
 * that device without changing what is on screen. Everywhere else (gestures, the dock, leaving
 * Cards) it is the device being held -- see [modeSwitchTargetDeviceClass].
 */
internal fun LauncherShellState.withSelectedHomeLayoutMode(
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
    targetDeviceClass: HomeLayoutDeviceClass = modeSwitchTargetDeviceClass,
): LauncherShellState {
    val resolvedMode = viewModeAvailability.availableModeOrStandard(targetDeviceClass, mode)
    val layoutSet =
        homeLayoutSet
            .withActiveLayout(homeLayout)
            .withModeChosenFor(deviceClass = targetDeviceClass, mode = resolvedMode)
            .also(homeLayoutRepository::saveHomeLayoutSet)

    return copy(homeLayout = layoutSet.activeLayout, homeLayoutSet = layoutSet)
}

/**
 * Make [mode] the Home of the Home ↔ Library pair of the device class Settings is editing (#1241).
 * A device class showing its Home switches to [mode]; one showing Library stays there (see
 * [withHomeMode]). Library, or a mode that device class cannot use, changes nothing.
 */
internal fun LauncherShellState.withSettingsHomeMode(
    mode: LauncherViewMode,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState {
    val deviceClass = settingsLayoutDeviceClass
    val canAdopt =
        mode in ModePair.HOME_MODES &&
            viewModeAvailability.isAvailable(deviceClass, mode) &&
            homeLayoutSet.modePairFor(deviceClass).home != mode
    if (!canAdopt) return this

    return homeLayoutSet
        .withActiveLayout(homeLayout)
        .withHomeMode(deviceClass = deviceClass, mode = mode)
        .also(homeLayoutRepository::saveHomeLayoutSet)
        .let { updated -> copy(homeLayout = updated.activeLayout, homeLayoutSet = updated) }
}

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
        val updatedLayoutSet =
            homeLayoutSet
                .withActiveLayout(homeLayout)
                // A template seeds pages; the dock is shared by every mode on the device and keeps
                // what the user built rather than taking the seed's default (#1205).
                .withLayoutKeepingDock(key = targetKey, layout = selectedLayout)
                .withModeChosenFor(deviceClass = targetDeviceClass, mode = mode)

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
    val layoutSet = homeLayoutSet
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
    val layoutSet =
        homeLayoutSet
            .withActiveLayout(homeLayout)
            .withLayout(key = key, layout = layout)
            .withModeChosenFor(deviceClass = settingsLayoutDeviceClass, mode = key.viewMode)

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

internal val LauncherShellState.settingsTargetLayout: HomeLayout
    get() = homeLayoutSet.withActiveLayout(homeLayout).layoutFor(settingsTargetLayoutKey)
