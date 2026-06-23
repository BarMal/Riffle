package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeLayoutDeviceClassClassifierTest {
    private val classifier = HomeLayoutDeviceClassClassifier()

    @Test
    fun classifiesCompactWidthsAsPhone() {
        assertEquals(HomeLayoutDeviceClass.PHONE, classifier.classify(screenWidthDp = 599))
    }

    @Test
    fun classifiesMediumWidthsAsFoldable() {
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, classifier.classify(screenWidthDp = 600))
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, classifier.classify(screenWidthDp = 839))
    }

    @Test
    fun classifiesExpandedWidthsAsTablet() {
        assertEquals(HomeLayoutDeviceClass.TABLET, classifier.classify(screenWidthDp = 840))
    }
}
