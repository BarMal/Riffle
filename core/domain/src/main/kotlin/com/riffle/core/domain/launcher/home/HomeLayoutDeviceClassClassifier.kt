package com.riffle.core.domain.launcher.home

class HomeLayoutDeviceClassClassifier {
    fun classify(screenWidthDp: Int): HomeLayoutDeviceClass =
        when {
            screenWidthDp >= TABLET_MIN_WIDTH_DP -> HomeLayoutDeviceClass.TABLET
            screenWidthDp >= FOLDABLE_MIN_WIDTH_DP -> HomeLayoutDeviceClass.FOLDABLE
            else -> HomeLayoutDeviceClass.PHONE
        }

    private companion object {
        const val FOLDABLE_MIN_WIDTH_DP = 600
        const val TABLET_MIN_WIDTH_DP = 840
    }
}
