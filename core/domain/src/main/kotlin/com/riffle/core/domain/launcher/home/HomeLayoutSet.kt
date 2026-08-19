package com.riffle.core.domain.launcher.home

data class HomeLayoutKey(
    val viewMode: LauncherViewMode,
    val deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
)

enum class HomeLayoutDeviceClass {
    PHONE,
    PHONE_LANDSCAPE,
    FOLDABLE,
    TABLET,
    DESKTOP,
}

data class HomeLayoutSet(
    val activeKey: HomeLayoutKey,
    val layouts: Map<HomeLayoutKey, HomeLayout>,
    val preferredModesByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode> =
        mapOf(activeKey.deviceClass to activeKey.viewMode),
    /**
     * The most recent non-Cards mode chosen on each device class -- where leaving Cards returns to.
     *
     * Distinct from [preferredModesByDeviceClass], which holds whatever is active *now* (Cards
     * included) so a device-class switch restores what you were last looking at there. This one is
     * only ever a non-Cards mode, so it survives entering Cards and can answer "where did I come
     * from" when you leave. Absent an entry -- a first run, or a decode of a layout written before
     * this was tracked -- leaving Cards falls back to [LauncherViewMode.STANDARD_APP_DRAWER], which
     * is where it always went before.
     */
    val lastNonCardsModeByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode> = emptyMap(),
) {
    val activeLayout: HomeLayout = layoutFor(activeKey)

    /** The mode to restore when leaving Cards on the active device. */
    fun modeLeavingCards(): LauncherViewMode {
        return lastNonCardsModeByDeviceClass[activeKey.deviceClass]
            ?: LauncherViewMode.STANDARD_APP_DRAWER
    }

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
                val layout = layouts[key] ?: defaultLayout(key).copy(dock = activeLayout.dock)
                copy(
                    activeKey = key,
                    layouts = layouts + (key to layout),
                    preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to mode),
                    // Entering Cards must not overwrite where leaving it returns to; every other
                    // mode is itself a valid return, so it records where you now are.
                    lastNonCardsModeByDeviceClass =
                        if (mode == LauncherViewMode.CARD_INTERFACE) {
                            lastNonCardsModeByDeviceClass
                        } else {
                            lastNonCardsModeByDeviceClass + (key.deviceClass to mode)
                        },
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
