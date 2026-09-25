package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeRing
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.modeSurface

/**
 * The mode a dock pull (or its accessibility and keyboard equivalents) switches to from [current]:
 * Library from any Home mode, and from Library the Home mode the device class uses -- the first
 * Home-surface mode in its [ring], else Cards.
 *
 * This is the one place the switch target is decided. The mode ring is being collapsed to the fixed
 * Home <-> Library pair (#1241); when it is, only this function's internals change.
 */
fun dockPullCounterpartMode(
    current: LauncherViewMode,
    ring: ModeRing,
): LauncherViewMode =
    when (current.modeSurface) {
        ModeSurface.HOME -> LauncherViewMode.HOME_SCREEN_LIBRARY
        ModeSurface.LIBRARY ->
            ring.modes.firstOrNull { mode -> mode.modeSurface == ModeSurface.HOME }
                ?: LauncherViewMode.CARD_INTERFACE
    }

/** [dockPullCounterpartMode] for [deviceClass], currently showing [current], in this set. */
fun HomeLayoutSet.dockPullCounterpartMode(
    deviceClass: HomeLayoutDeviceClass,
    current: LauncherViewMode,
): LauncherViewMode = dockPullCounterpartMode(current = current, ring = modeRingFor(deviceClass))
