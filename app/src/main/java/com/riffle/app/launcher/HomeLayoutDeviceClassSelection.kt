package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClassClassifier

internal data class HomeLayoutDeviceClassSelection(
    val activeDeviceClass: HomeLayoutDeviceClass,
    val availableDeviceClasses: Set<HomeLayoutDeviceClass>,
)

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
    homeLayoutDeviceClassSelectionFromWindowLayout(
        hasFoldingFeature = hasFoldingFeature,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
    )?.activeDeviceClass

internal fun homeLayoutDeviceClassSelectionFromWindowLayout(
    hasFoldingFeature: Boolean,
    screenWidthDp: Int,
    screenHeightDp: Int,
): HomeLayoutDeviceClassSelection? {
    val configurationClass =
        homeLayoutDeviceClassFromConfiguration(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
        ) ?: return null

    val activeDeviceClass =
        when {
            hasFoldingFeature && configurationClass != HomeLayoutDeviceClass.PHONE -> HomeLayoutDeviceClass.FOLDABLE
            else -> configurationClass
        }
    val availableDeviceClasses =
        when {
            hasFoldingFeature -> setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE)
            else -> setOf(activeDeviceClass)
        }

    return HomeLayoutDeviceClassSelection(
        activeDeviceClass = activeDeviceClass,
        availableDeviceClasses = availableDeviceClasses,
    )
}
