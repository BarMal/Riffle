package com.riffle.core.domain.launcher.home

/** The active device class's mode ring (#1225). */
val HomeLayoutSet.activeModeRing: ModeRing
    get() = modeRingFor(activeKey.deviceClass)

/** Where the active mode sits in [activeModeRing]. The active mode is always in its ring. */
val HomeLayoutSet.activeModeIndex: Int
    get() = activeModeRing.indexOf(activeKey.viewMode)

/** The mode after the active one in the active device class's ring. */
fun HomeLayoutSet.nextMode(): LauncherViewMode = activeModeRing.next(activeKey.viewMode)

/** The mode before the active one in the active device class's ring; also how Cards is left. */
fun HomeLayoutSet.previousMode(): LauncherViewMode = activeModeRing.previous(activeKey.viewMode)

/**
 * Replace [deviceClass]'s ring with [ring]. When the mode that device class shows is no longer in
 * it, the device class moves to the mode's neighbour (see [ModeRing.neighbourAfterRemoving]); on the
 * active device class that switches what is on screen.
 */
fun HomeLayoutSet.withModeRing(
    deviceClass: HomeLayoutDeviceClass,
    ring: ModeRing,
): HomeLayoutSet {
    val previousRing = modeRingFor(deviceClass)
    val current = currentModeOf(deviceClass)
    val updated = copy(modeRingsByDeviceClass = modeRingsByDeviceClass + (deviceClass to ring))
    if (current == null || current in ring) return updated

    val neighbour = previousRing.neighbourAfterRemoving(removed = current, updated = ring)
    return updated.withModeChosenFor(deviceClass = deviceClass, mode = neighbour)
}

/**
 * This set with the mode rings a decoder found restored (#1225).
 *
 * [storedRings] is what a set written since rings existed holds, or null for one written before.
 * An older set is migrated: each device class's ring is built from its preferred mode and
 * [legacyLastNonCardsModes], the mode leaving Cards used to return to (see [ModeRing.migrated]).
 *
 * Either way, every device class's current mode ends up in its ring, so stored data that disagrees
 * with itself (a ring edited by hand, a mode made unavailable) cannot break the invariant.
 *
 * Restoring adds nothing a set did not hold: a device class without a stored ring keeps none (its
 * [ModeRing.fallbackFor] ring already holds its mode), a stored ring is only rewritten when it is
 * missing its mode, and a migrated ring equal to the fallback is not stored. So encoding a set and
 * decoding it gives back an equal set (#1225).
 */
fun HomeLayoutSet.withRestoredModeRings(
    storedRings: Map<HomeLayoutDeviceClass, ModeRing>?,
    legacyLastNonCardsModes: Map<HomeLayoutDeviceClass, LauncherViewMode> = emptyMap(),
): HomeLayoutSet {
    val currentModes = preferredModesByDeviceClass + (activeKey.deviceClass to activeKey.viewMode)
    val rings =
        storedRings
            ?: ModeRing.migratedByDeviceClass(
                preferredModes = currentModes,
                lastNonCardsModes = legacyLastNonCardsModes,
            ).filter { (deviceClass, ring) -> ring != ModeRing.fallbackFor(currentModes[deviceClass]) }

    return copy(
        modeRingsByDeviceClass =
            rings.mapValues { (deviceClass, ring) ->
                currentModes[deviceClass]?.let { mode -> ring.including(mode) } ?: ring
            },
    )
}

/** What [deviceClass] shows now: the active mode on the active device, its preference elsewhere. */
internal fun HomeLayoutSet.currentModeOf(deviceClass: HomeLayoutDeviceClass): LauncherViewMode? =
    if (deviceClass == activeKey.deviceClass) activeKey.viewMode else preferredModesByDeviceClass[deviceClass]

/** Rings with [mode] added to [deviceClass]'s when it is missing, after [after]. */
internal fun HomeLayoutSet.modeRingsIncluding(
    deviceClass: HomeLayoutDeviceClass,
    mode: LauncherViewMode,
    after: LauncherViewMode?,
): Map<HomeLayoutDeviceClass, ModeRing> =
    modeRingsByDeviceClass + (deviceClass to modeRingFor(deviceClass).including(mode = mode, after = after))
