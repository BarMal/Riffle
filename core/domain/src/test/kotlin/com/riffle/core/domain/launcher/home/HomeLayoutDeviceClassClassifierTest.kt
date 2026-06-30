package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutDeviceClassClassifierTest {
    private val classifier = HomeLayoutDeviceClassClassifier()

    @Test
    fun classifiesCompactWidthsAsPhone() {
        assertEquals(HomeLayoutDeviceClass.PHONE, classifier.classify(screenWidthDp = 599, screenHeightDp = 900))
    }

    @Test
    fun classifiesCompactHeightsAsPhone() {
        assertEquals(HomeLayoutDeviceClass.PHONE, classifier.classify(screenWidthDp = 700, screenHeightDp = 599))
    }

    @Test
    fun classifiesMediumWidthsAsFoldable() {
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, classifier.classify(screenWidthDp = 600, screenHeightDp = 900))
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, classifier.classify(screenWidthDp = 839, screenHeightDp = 900))
    }

    @Test
    fun classifiesExpandedWidthsAsTablet() {
        assertEquals(HomeLayoutDeviceClass.TABLET, classifier.classify(screenWidthDp = 840, screenHeightDp = 900))
    }
}
