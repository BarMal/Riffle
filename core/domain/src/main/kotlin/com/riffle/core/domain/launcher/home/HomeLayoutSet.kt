package com.riffle.core.domain.launcher.home

data class HomeLayoutKey(
    val viewMode: LauncherViewMode,
    val deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
)

enum class HomeLayoutDeviceClass {
    PHONE,
    FOLDABLE,
    TABLET,
}

data class HomeLayoutSet(
    val activeKey: HomeLayoutKey,
    val layouts: Map<HomeLayoutKey, HomeLayout>,
    val preferredModesByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode> =
        mapOf(activeKey.deviceClass to activeKey.viewMode),
) {
    val activeLayout: HomeLayout = layoutFor(activeKey)

    fun layoutFor(key: HomeLayoutKey): HomeLayout = layouts[key] ?: defaultLayout(key)

    fun withActiveLayout(layout: HomeLayout): HomeLayoutSet =
        copy(layouts = layouts + (activeKey to layout.copy(viewMode = activeKey.viewMode)))

    fun withLayout(
        key: HomeLayoutKey,
        layout: HomeLayout,
    ): HomeLayoutSet = copy(layouts = layouts + (key to layout.copy(viewMode = key.viewMode)))

    fun withPreferredMode(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ): HomeLayoutSet = copy(preferredModesByDeviceClass = preferredModesByDeviceClass + (deviceClass to mode))

    fun selectMode(mode: LauncherViewMode): HomeLayoutSet =
        activeKey.copy(viewMode = mode)
            .let { key ->
                copy(
                    activeKey = key,
                    layouts = layouts + (key to layoutFor(key)),
                    preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to mode),
                )
            }

    fun selectMode(
        mode: LauncherViewMode,
        availability: LauncherViewModeAvailability,
    ): HomeLayoutSet = selectMode(availability.availableModeOrStandard(activeKey.deviceClass, mode))

    fun selectDeviceClass(deviceClass: HomeLayoutDeviceClass): HomeLayoutSet =
        HomeLayoutKey(
            viewMode = preferredModesByDeviceClass[deviceClass] ?: activeKey.viewMode,
            deviceClass = deviceClass,
        ).let { key ->
            copy(
                activeKey = key,
                layouts = layouts + (key to layoutFor(key)),
                preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to key.viewMode),
            )
        }

    fun selectDeviceClass(
        deviceClass: HomeLayoutDeviceClass,
        availability: LauncherViewModeAvailability,
    ): HomeLayoutSet {
        val preferredMode = preferredModesByDeviceClass[deviceClass] ?: activeKey.viewMode
        val key =
            HomeLayoutKey(
                viewMode = availability.availableModeOrStandard(deviceClass, preferredMode),
                deviceClass = deviceClass,
            )
        val preferredModes =
            if (key.viewMode == preferredMode) {
                preferredModesByDeviceClass + (key.deviceClass to key.viewMode)
            } else {
                preferredModesByDeviceClass
            }

        return copy(
            activeKey = key,
            layouts = layouts + (key to layoutFor(key)),
            preferredModesByDeviceClass = preferredModes,
        )
    }

    companion object {
        fun standard(): HomeLayoutSet = fromLayout(HomeLayoutDefaults.standard())

        fun fromLayout(layout: HomeLayout): HomeLayoutSet =
            HomeLayoutKey(viewMode = layout.viewMode)
                .let { key -> HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout)) }

        fun defaultLayout(key: HomeLayoutKey): HomeLayout =
            HomeLayoutDefaults
                .standard(key.deviceClass)
                .copy(viewMode = key.viewMode)
    }
}
