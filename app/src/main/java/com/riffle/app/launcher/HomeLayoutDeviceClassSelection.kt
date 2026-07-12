package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClassClassifier

internal data class HomeLayoutDeviceClassSelection(
    val activeDeviceClass: HomeLayoutDeviceClass,
    val availableDeviceClasses: Set<HomeLayoutDeviceClass>,
)

internal enum class HomeLayoutFoldablePosture {
    NONE,
    FOLDED,
    HALF_OPEN,
    UNFOLDED,
}

internal fun homeLayoutFoldablePosture(
    hasFoldableHardware: Boolean,
    hasFoldingFeature: Boolean,
    hasUnfoldedFoldingFeature: Boolean,
    configurationClass: HomeLayoutDeviceClass?,
    hasHalfOpenedFoldingFeature: Boolean = false,
): HomeLayoutFoldablePosture =
    when {
        hasHalfOpenedFoldingFeature -> HomeLayoutFoldablePosture.HALF_OPEN
        hasUnfoldedFoldingFeature -> HomeLayoutFoldablePosture.UNFOLDED
        hasFoldingFeature -> HomeLayoutFoldablePosture.FOLDED
        hasFoldableHardware && configurationClass != HomeLayoutDeviceClass.PHONE -> HomeLayoutFoldablePosture.UNFOLDED
        hasFoldableHardware -> HomeLayoutFoldablePosture.FOLDED
        else -> HomeLayoutFoldablePosture.NONE
    }

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
    foldablePosture: HomeLayoutFoldablePosture,
    screenWidthDp: Int,
    screenHeightDp: Int,
): HomeLayoutDeviceClass? =
    homeLayoutDeviceClassSelectionFromWindowLayout(
        foldablePosture = foldablePosture,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
    )?.activeDeviceClass

internal fun homeLayoutDeviceClassSelectionFromWindowLayout(
    foldablePosture: HomeLayoutFoldablePosture,
    screenWidthDp: Int,
    screenHeightDp: Int,
): HomeLayoutDeviceClassSelection? {
    val configurationClass =
        homeLayoutDeviceClassFromConfiguration(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
        ) ?: return null

    val activeDeviceClass =
        when (foldablePosture) {
            HomeLayoutFoldablePosture.FOLDED -> HomeLayoutDeviceClass.PHONE
            HomeLayoutFoldablePosture.HALF_OPEN,
            HomeLayoutFoldablePosture.UNFOLDED,
            ->
                configurationClass.takeIf { it == HomeLayoutDeviceClass.DESKTOP } ?: HomeLayoutDeviceClass.FOLDABLE
            HomeLayoutFoldablePosture.NONE -> configurationClass
        }
    val availableDeviceClasses =
        when (foldablePosture) {
            HomeLayoutFoldablePosture.FOLDED,
            HomeLayoutFoldablePosture.HALF_OPEN,
            HomeLayoutFoldablePosture.UNFOLDED,
            ->
                setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE) +
                    setOfNotNull(configurationClass.takeIf { it == HomeLayoutDeviceClass.DESKTOP })

            HomeLayoutFoldablePosture.NONE -> setOf(activeDeviceClass)
        }

    return HomeLayoutDeviceClassSelection(
        activeDeviceClass = activeDeviceClass,
        availableDeviceClasses = availableDeviceClasses,
    )
}
