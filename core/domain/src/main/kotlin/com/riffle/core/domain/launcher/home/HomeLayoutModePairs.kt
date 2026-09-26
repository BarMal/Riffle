package com.riffle.core.domain.launcher.home

/** The active device class's Home ↔ Library pair (#1241). */
val HomeLayoutSet.activeModePair: ModePair
    get() = modePairFor(activeKey.deviceClass)

/**
 * This set with [mode] as [deviceClass]'s Home (Settings' "Home screen" choice). When that device
 * class is showing its Home, it switches to [mode] -- on the active device class that changes what
 * is on screen; when it is showing Library it stays there, and the next pull back lands on [mode].
 * Library is never a Home: asking for it returns this set unchanged.
 */
fun HomeLayoutSet.withHomeMode(
    deviceClass: HomeLayoutDeviceClass,
    mode: LauncherViewMode,
): HomeLayoutSet {
    val pair = ModePair.of(mode) ?: return this
    val current = currentModeOf(deviceClass)
    return if (current != null && current.modeSurface == ModeSurface.HOME) {
        withModeChosenFor(deviceClass = deviceClass, mode = mode)
    } else {
        copy(modePairsByDeviceClass = modePairsByDeviceClass + (deviceClass to pair))
    }
}

/**
 * This set with the mode pairs a decoder found restored (#1241).
 *
 * [storedPairs] is what a set written since pairs existed holds, or null for an older one. An older
 * set is migrated from [legacyRings], the mode rings of #1225 (see [ModePair.fromLegacyRing]), or,
 * for one written before rings too, from its preferred modes and [legacyLastNonCardsModes], where
 * leaving Cards used to return to (see [ModePair.migrated]).
 *
 * Either way, a device class showing a Home mode ends up with that mode as its Home, so stored data
 * that disagrees with itself cannot break the invariant that the current mode is one of the pair's.
 * A device class nothing stored a pair or ring for keeps none: its [ModePair.fallbackFor] already
 * matches what a ring-era fallback ring would have led to.
 */
fun HomeLayoutSet.withRestoredModePairs(
    storedPairs: Map<HomeLayoutDeviceClass, ModePair>?,
    legacyRings: Map<HomeLayoutDeviceClass, List<LauncherViewMode>>? = null,
    legacyLastNonCardsModes: Map<HomeLayoutDeviceClass, LauncherViewMode> = emptyMap(),
): HomeLayoutSet {
    val currentModes = preferredModesByDeviceClass + (activeKey.deviceClass to activeKey.viewMode)
    val pairs =
        storedPairs
            ?: legacyRings?.mapValues { (deviceClass, ring) ->
                ModePair.fromLegacyRing(ringModes = ring, currentMode = currentModes[deviceClass])
            }
            ?: (currentModes.keys + legacyLastNonCardsModes.keys).associateWith { deviceClass ->
                ModePair.migrated(
                    preferredMode = currentModes[deviceClass],
                    lastNonCardsMode = legacyLastNonCardsModes[deviceClass],
                )
            }

    return copy(
        modePairsByDeviceClass =
            pairs.mapValues { (deviceClass, pair) -> ModePair.of(currentModes[deviceClass]) ?: pair },
    )
}

/** What [deviceClass] shows now: the active mode on the active device, its preference elsewhere. */
internal fun HomeLayoutSet.currentModeOf(deviceClass: HomeLayoutDeviceClass): LauncherViewMode? =
    if (deviceClass == activeKey.deviceClass) activeKey.viewMode else preferredModesByDeviceClass[deviceClass]

/**
 * Pairs with [deviceClass]'s pair recorded as it stands before it shows [mode], and with [mode] as
 * its Home when [mode] is a Home mode. Recording it keeps the Home a device class was on when it
 * moves to Library, so the way back leads to the same Home.
 */
internal fun HomeLayoutSet.modePairsShowing(
    deviceClass: HomeLayoutDeviceClass,
    mode: LauncherViewMode,
): Map<HomeLayoutDeviceClass, ModePair> =
    modePairsByDeviceClass + (deviceClass to (ModePair.of(mode) ?: modePairFor(deviceClass)))
