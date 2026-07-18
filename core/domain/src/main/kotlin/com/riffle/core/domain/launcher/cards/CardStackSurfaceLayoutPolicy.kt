package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/** Chooses the focused-card arrangement for the available launcher window class. */
class CardStackSurfaceLayoutPolicy {
    fun layoutFor(deviceClass: HomeLayoutDeviceClass): CardStackSurfaceLayout =
        when (deviceClass) {
            HomeLayoutDeviceClass.FOLDABLE,
            HomeLayoutDeviceClass.TABLET,
            HomeLayoutDeviceClass.DESKTOP,
            -> CardStackSurfaceLayout.SIDE_BY_SIDE

            HomeLayoutDeviceClass.PHONE,
            HomeLayoutDeviceClass.PHONE_LANDSCAPE,
            -> CardStackSurfaceLayout.CENTER_STAGE
        }
}

enum class CardStackSurfaceLayout {
    /** Focused cards lead the vertical flow, with related content below. */
    CENTER_STAGE,

    /** Focused cards and related content remain visible next to each other. */
    SIDE_BY_SIDE,
}
