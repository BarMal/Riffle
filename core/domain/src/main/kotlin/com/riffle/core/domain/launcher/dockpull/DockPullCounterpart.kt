package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode

/**
 * The mode a dock pull (or its accessibility and keyboard equivalents) switches [deviceClass] to
 * from [current]: the other side of its Home <-> Library pair (`ModePair.counterpart`).
 */
fun HomeLayoutSet.dockPullCounterpartMode(
    deviceClass: HomeLayoutDeviceClass,
    current: LauncherViewMode,
): LauncherViewMode = modePairFor(deviceClass).counterpart(current)
