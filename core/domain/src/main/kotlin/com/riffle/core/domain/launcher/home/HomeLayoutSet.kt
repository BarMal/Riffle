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

/**
 * Every home layout Riffle holds: one per (view mode x device class), plus exactly one dock per
 * device class.
 *
 * The dock is deliberately not per mode (#1205, docs/product/dock.md): the same pinned items, edge,
 * size, appearance and dynamic-section budgets apply in Standard, Library and Cards alike, so it
 * can stay put while the mode changes around it. [layouts] still stores a [HomeLayout] per key --
 * its pages, template and settings are genuinely per mode -- but whatever dock a stored layout
 * carries is not authoritative. Read layouts through [layoutFor] / [activeLayout], which always
 * hand back the device class's shared dock, and write them through [withLayout] /
 * [withActiveLayout], which store the layout's dock as that shared dock. That keeps every existing
 * dock edit (pin, reorder, move to or from home, settings) working unchanged while writing to the
 * one dock whatever mode it was made in.
 */
data class HomeLayoutSet(
    val activeKey: HomeLayoutKey,
    val layouts: Map<HomeLayoutKey, HomeLayout>,
    val preferredModesByDeviceClass: Map<HomeLayoutDeviceClass, LauncherViewMode> =
        mapOf(activeKey.deviceClass to activeKey.viewMode),
    /**
     * The Home ↔ Library pair each device class moves between (#1241): which mode its Home is.
     *
     * A device class with no entry uses [ModePair.fallbackFor] the mode it shows. Whichever mode a
     * device class shows -- the active mode, or its entry in [preferredModesByDeviceClass] -- is
     * always one of its pair's two: every way of choosing a mode here records a Home mode it chooses
     * as that device class's Home. Read pairs through [modePairFor]; choose Home with
     * HomeLayoutModePairs.kt's [withHomeMode].
     */
    val modePairsByDeviceClass: Map<HomeLayoutDeviceClass, ModePair> = emptyMap(),
    /**
     * The one dock each device class has, shared by every mode on it.
     *
     * Defaults to the dock of the mode each device class currently shows (see [docksChosenFrom]),
     * which is what a set assembled from per-mode layouts -- a test fixture, or a caller that
     * predates the shared dock -- means by "its dock". Decoding a stored set written before the
     * dock was shared goes through [unifiedLegacyDocks] instead, which also rescues pins that only
     * another mode's dock held.
     */
    val docks: Map<HomeLayoutDeviceClass, DockModel> =
        docksChosenFrom(activeKey, layouts, preferredModesByDeviceClass),
    /**
     * The edge each device class's shared dock takes in Library ([ModeSurface.LIBRARY]).
     *
     * The dock's content is shared, but its edge is per surface: Home's edge is the shared dock's
     * own [DockModel.position], and Library's is stored here. A device class with no entry uses
     * [DEFAULT_LIBRARY_DOCK_EDGE]. Read and write it through ModeDockEdges.kt ([dockEdgeFor],
     * [withDockEdge]).
     */
    val libraryDockEdgesByDeviceClass: Map<HomeLayoutDeviceClass, DockPosition> = emptyMap(),
) {
    val activeLayout: HomeLayout = layoutFor(activeKey)

    /** [deviceClass]'s Home ↔ Library pair. Choosing Home and restoring are in HomeLayoutModePairs.kt. */
    fun modePairFor(deviceClass: HomeLayoutDeviceClass): ModePair =
        modePairsByDeviceClass[deviceClass]
            ?: ModePair.fallbackFor(currentModeOf(deviceClass))

    /** [key]'s layout, carrying its device class's shared dock. */
    fun layoutFor(key: HomeLayoutKey): HomeLayout =
        (layouts[key] ?: defaultLayout(key)).withSharedDock(dockFor(key.deviceClass))

    /** Stores [layout] as the active one; its dock becomes the active device class's dock. */
    fun withActiveLayout(layout: HomeLayout): HomeLayoutSet = withLayout(activeKey, layout)

    /** Stores [layout] under [key]; its dock becomes [key]'s device class dock, in every mode. */
    fun withLayout(
        key: HomeLayoutKey,
        layout: HomeLayout,
    ): HomeLayoutSet =
        copy(
            layouts = layouts + (key to layout.copy(viewMode = key.viewMode)),
            docks = docks + (key.deviceClass to layout.dock),
        )

    fun withPreferredMode(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ): HomeLayoutSet =
        copy(
            preferredModesByDeviceClass = preferredModesByDeviceClass + (deviceClass to mode),
            modePairsByDeviceClass = modePairsShowing(deviceClass = deviceClass, mode = mode),
        )

    fun selectMode(mode: LauncherViewMode): HomeLayoutSet =
        activeKey.copy(viewMode = mode)
            .let { key ->
                // The dock is not part of what a mode owns: a layout created here shows the
                // device class's shared dock through layoutFor, like every other.
                val layout = layouts[key] ?: defaultLayout(key)
                copy(
                    activeKey = key,
                    layouts = layouts + (key to layout),
                    preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to mode),
                    // The active mode is always one of its pair's: a Home mode chosen here (Settings'
                    // mode picker, a template) becomes the device class's Home.
                    modePairsByDeviceClass = modePairsShowing(deviceClass = key.deviceClass, mode = mode),
                )
            }

    fun selectMode(
        mode: LauncherViewMode,
        availability: LauncherViewModeAvailability,
    ): HomeLayoutSet = selectMode(availability.availableModeOrStandard(activeKey.deviceClass, mode))

    /**
     * Record [mode] as [deviceClass]'s preference, switching to it only when [deviceClass] is the
     * device being held. A mode chosen for another device class applies when that device is next
     * the active one.
     */
    fun withModeChosenFor(
        deviceClass: HomeLayoutDeviceClass,
        mode: LauncherViewMode,
    ): HomeLayoutSet =
        withPreferredMode(deviceClass = deviceClass, mode = mode).let { layouts ->
            if (layouts.activeKey.deviceClass == deviceClass) layouts.selectMode(mode) else layouts
        }

    fun selectDeviceClass(deviceClass: HomeLayoutDeviceClass): HomeLayoutSet =
        HomeLayoutKey(
            viewMode = preferredModesByDeviceClass[deviceClass] ?: activeKey.viewMode,
            deviceClass = deviceClass,
        ).let { key ->
            copy(
                activeKey = key,
                layouts = layouts + (key to layoutFor(key)),
                preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to key.viewMode),
                modePairsByDeviceClass = modePairsShowing(deviceClass = deviceClass, mode = key.viewMode),
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
            modePairsByDeviceClass = modePairsShowing(deviceClass = deviceClass, mode = key.viewMode),
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
