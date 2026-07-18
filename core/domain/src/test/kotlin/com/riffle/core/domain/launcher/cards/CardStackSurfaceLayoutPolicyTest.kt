package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals

class CardStackSurfaceLayoutPolicyTest {
    private val policy = CardStackSurfaceLayoutPolicy()

    @Test
    fun phoneAndFoldedWindowsKeepTheFocusedCardCenterStage() {
        assertEquals(CardStackSurfaceLayout.CENTER_STAGE, policy.layoutFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(CardStackSurfaceLayout.CENTER_STAGE, policy.layoutFor(HomeLayoutDeviceClass.PHONE_LANDSCAPE))
    }

    @Test
    fun widerWindowsKeepCardContextAlongsideTheFocusedCard() {
        assertEquals(CardStackSurfaceLayout.SIDE_BY_SIDE, policy.layoutFor(HomeLayoutDeviceClass.FOLDABLE))
        assertEquals(CardStackSurfaceLayout.SIDE_BY_SIDE, policy.layoutFor(HomeLayoutDeviceClass.TABLET))
        assertEquals(CardStackSurfaceLayout.SIDE_BY_SIDE, policy.layoutFor(HomeLayoutDeviceClass.DESKTOP))
    }
}
