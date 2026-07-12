package com.riffle.core.domain.launcher.home

class HomeLayoutDeviceClassClassifier {
    fun classify(
        screenWidthDp: Int,
        screenHeightDp: Int,
    ): HomeLayoutDeviceClass =
        when {
            screenWidthDp >= DESKTOP_MIN_WIDTH_DP -> HomeLayoutDeviceClass.DESKTOP
            minOf(screenWidthDp, screenHeightDp) < FOLDABLE_MIN_SHORT_SIDE_DP -> HomeLayoutDeviceClass.PHONE
            screenWidthDp >= TABLET_MIN_WIDTH_DP -> HomeLayoutDeviceClass.TABLET
            screenWidthDp >= FOLDABLE_MIN_WIDTH_DP -> HomeLayoutDeviceClass.FOLDABLE
            else -> HomeLayoutDeviceClass.PHONE
        }

    private companion object {
        const val FOLDABLE_MIN_SHORT_SIDE_DP = 600
        const val FOLDABLE_MIN_WIDTH_DP = 600
        const val TABLET_MIN_WIDTH_DP = 840
        const val DESKTOP_MIN_WIDTH_DP = 1_200
    }
}
