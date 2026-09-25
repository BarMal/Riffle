package com.riffle.core.domain.launcher.home

/**
 * The two surfaces a device class moves between with the dock pull: Home and Library.
 *
 * Home is whatever the device class uses as its home screen (Cards, or Standard until Cards covers
 * widgets and standard app pages); Library is the app drawer. The dock is shared by both (one
 * [DockModel] per device class, #1205), but each surface places it on its own edge, see
 * [ModeDockEdges].
 */
enum class ModeSurface {
    HOME,
    LIBRARY,
    ;

    /** The surface a dock pull from this one leads to. */
    val other: ModeSurface
        get() =
            when (this) {
                HOME -> LIBRARY
                LIBRARY -> HOME
            }
}

/** The surface a [LauncherViewMode] is shown as: Library for the library mode, Home for the rest. */
val LauncherViewMode.modeSurface: ModeSurface
    get() =
        when (this) {
            LauncherViewMode.HOME_SCREEN_LIBRARY -> ModeSurface.LIBRARY
            LauncherViewMode.STANDARD_APP_DRAWER,
            LauncherViewMode.CARD_INTERFACE,
            -> ModeSurface.HOME
        }

/** Whether a dock on an edge runs as a horizontal strip or a vertical column. */
enum class DockOrientation {
    HORIZONTAL,
    VERTICAL,
}

val DockPosition.dockOrientation: DockOrientation
    get() = if (isHorizontalEdge) DockOrientation.HORIZONTAL else DockOrientation.VERTICAL

/** The edge Library places the dock on when nothing has chosen one. */
val DEFAULT_LIBRARY_DOCK_EDGE: DockPosition = DockPosition.BOTTOM

/**
 * The edge Home falls back to when neither the user nor the device class's template names one.
 * Matches the app's `resolveDockPosition` fallback.
 */
val FALLBACK_HOME_DOCK_EDGE: DockPosition = DockPosition.LEFT

/**
 * One device class's resolved dock edge per [ModeSurface].
 *
 * The same edge for both surfaces is allowed: the pull then leads out of the dock's edge and the
 * dock settles back onto it.
 */
data class ModeDockEdges(
    val home: DockPosition,
    val library: DockPosition,
) {
    fun edgeFor(surface: ModeSurface): DockPosition =
        when (surface) {
            ModeSurface.HOME -> home
            ModeSurface.LIBRARY -> library
        }

    fun orientationFor(surface: ModeSurface): DockOrientation = edgeFor(surface).dockOrientation
}

/**
 * The dock edge [deviceClass] stores for [surface], or null when it stores none.
 *
 * Home's edge is the shared dock's own [DockModel.position], which every existing dock setting and
 * layout reads (null means "the template's edge"). Library's edge is kept beside the shared dock in
 * [HomeLayoutSet.libraryDockEdgesByDeviceClass].
 */
fun HomeLayoutSet.storedDockEdgeFor(
    deviceClass: HomeLayoutDeviceClass,
    surface: ModeSurface,
): DockPosition? =
    when (surface) {
        ModeSurface.HOME -> dockFor(deviceClass).position
        ModeSurface.LIBRARY -> libraryDockEdgesByDeviceClass[deviceClass]
    }

/**
 * The edge [deviceClass]'s dock sits on in [surface]. Home: the configured edge, else
 * [templateEdge], else [FALLBACK_HOME_DOCK_EDGE]. Library: the stored edge, else
 * [DEFAULT_LIBRARY_DOCK_EDGE].
 */
fun HomeLayoutSet.dockEdgeFor(
    deviceClass: HomeLayoutDeviceClass,
    surface: ModeSurface,
    templateEdge: DockPosition? = null,
): DockPosition =
    storedDockEdgeFor(deviceClass, surface)
        ?: when (surface) {
            ModeSurface.HOME -> templateEdge ?: FALLBACK_HOME_DOCK_EDGE
            ModeSurface.LIBRARY -> DEFAULT_LIBRARY_DOCK_EDGE
        }

/** Both of [deviceClass]'s surface edges, resolved as [dockEdgeFor] does. */
fun HomeLayoutSet.modeDockEdgesFor(
    deviceClass: HomeLayoutDeviceClass,
    templateEdge: DockPosition? = null,
): ModeDockEdges =
    ModeDockEdges(
        home = dockEdgeFor(deviceClass, ModeSurface.HOME, templateEdge),
        library = dockEdgeFor(deviceClass, ModeSurface.LIBRARY, templateEdge),
    )

/**
 * This set with [deviceClass]'s dock on [edge] in [surface]; the other surface keeps its edge.
 *
 * Moving Home's edge moves the shared dock's [DockModel.position], exactly as the dock-position
 * setting does today, and every mode's stored pages are refitted to it by [HomeLayoutSet.layoutFor].
 * Library's edge is only recorded here; nothing yet draws Library with its own edge.
 */
fun HomeLayoutSet.withDockEdge(
    deviceClass: HomeLayoutDeviceClass,
    surface: ModeSurface,
    edge: DockPosition,
): HomeLayoutSet =
    when (surface) {
        ModeSurface.HOME -> copy(docks = docks + (deviceClass to dockFor(deviceClass).copy(position = edge)))
        ModeSurface.LIBRARY ->
            copy(libraryDockEdgesByDeviceClass = libraryDockEdgesByDeviceClass + (deviceClass to edge))
    }

/**
 * Migrates a set decoded from before the dock edge was per surface.
 *
 * Such a set had one edge per device class, shown in every mode. To keep what the user sees, a
 * device class that chose an edge keeps it in both surfaces: Library is given that same edge. A
 * device class that never chose one (its dock follows the template) gets no Library entry, so
 * Library takes [DEFAULT_LIBRARY_DOCK_EDGE]. Device classes that already have a Library edge are left
 * alone, so running this twice changes nothing.
 */
fun HomeLayoutSet.withLibraryDockEdgesMigrated(): HomeLayoutSet {
    val deviceClasses = (layouts.keys.map { key -> key.deviceClass } + docks.keys).distinct()
    val migrated =
        deviceClasses
            .filterNot { deviceClass -> deviceClass in libraryDockEdgesByDeviceClass }
            .mapNotNull { deviceClass -> dockFor(deviceClass).position?.let { edge -> deviceClass to edge } }
            .toMap()
    if (migrated.isEmpty()) return this
    return copy(libraryDockEdgesByDeviceClass = libraryDockEdgesByDeviceClass + migrated)
}
