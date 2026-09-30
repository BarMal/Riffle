package com.riffle.core.domain.launcher.home

data class LauncherViewModeAvailability(
    val enabledExperimentalModesByDeviceClass: Map<HomeLayoutDeviceClass, Set<LauncherViewMode>> = emptyMap(),
    /** Modes available on every device class without being enabled. */
    val alwaysAvailableModes: Set<LauncherViewMode> = setOf(LauncherViewMode.STANDARD_APP_DRAWER),
    /** The mode anything unavailable resolves to. Must be available. */
    val fallbackMode: LauncherViewMode = LauncherViewMode.STANDARD_APP_DRAWER,
) {
    fun availableModes(deviceClass: HomeLayoutDeviceClass): List<LauncherViewMode> =
        LauncherViewMode.entries.filter { mode ->
            mode in alwaysAvailableModes ||
                enabledExperimentalModesByDeviceClass[deviceClass].orEmpty().contains(mode)
        }

    fun isAvailable(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ): Boolean = availableModes(deviceClass).contains(mode)

    fun availableModeOrStandard(
        deviceClass: HomeLayoutDeviceClass,
        preferredMode: LauncherViewMode?,
    ): LauncherViewMode =
        preferredMode
            ?.takeIf { mode -> isAvailable(deviceClass, mode) }
            ?: fallbackMode

    fun availableKeyFor(
        layoutSet: HomeLayoutSet,
        deviceClass: HomeLayoutDeviceClass,
    ): HomeLayoutKey =
        HomeLayoutKey(
            viewMode =
                availableModeOrStandard(
                    deviceClass = deviceClass,
                    preferredMode = layoutSet.preferredModesByDeviceClass[deviceClass] ?: layoutSet.activeKey.viewMode,
                ),
            deviceClass = deviceClass,
        )
}
