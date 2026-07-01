package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClassClassifier

internal fun homeLayoutDeviceClassFromConfiguration(
    screenWidthDp: Int,
    screenHeightDp: Int,
): HomeLayoutDeviceClass? =
    when {
        screenWidthDp <= 0 || screenHeightDp <= 0 -> null
        else ->
            HomeLayoutDeviceClassClassifier().classify(
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
            )
    }

internal fun homeLayoutDeviceClassFromWindowLayout(
    hasFoldingFeature: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int,
): HomeLayoutDeviceClass? =
    when {
        hasFoldingFeature -> HomeLayoutDeviceClass.FOLDABLE
        else ->
            homeLayoutDeviceClassFromConfiguration(
                screenWidthDp = screenWidthDp,
                screenHeightDp = screenHeightDp,
            )
    }
