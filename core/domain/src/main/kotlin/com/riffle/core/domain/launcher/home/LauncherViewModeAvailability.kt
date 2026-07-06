package com.riffle.core.domain.launcher.home

data class LauncherViewModeAvailability(
    val enabledExperimentalModesByDeviceClass: Map<HomeLayoutDeviceClass, Set<LauncherViewMode>> = emptyMap(),
) {
    fun availableModes(deviceClass: HomeLayoutDeviceClass): List<LauncherViewMode> =
        LauncherViewMode.entries.filter { mode ->
            mode == LauncherViewMode.STANDARD_APP_DRAWER ||
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
            ?: LauncherViewMode.STANDARD_APP_DRAWER

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
