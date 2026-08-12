package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class CardStackSettleDurationTest {
    @Test
    fun aSingleStepSettleUsesTheBaseDurationUnchanged() {
        assertEquals(220, cardStackSettleDurationMillis(baseDurationMillis = 220, settleStepCount = 1))
    }

    @Test
    fun aMultiStepSettleScalesDurationByHowManyCardsItSkips() {
        assertEquals(660, cardStackSettleDurationMillis(baseDurationMillis = 220, settleStepCount = 3))
    }

    @Test
    fun scalingIsCappedSoAnExtremeSkipDoesNotSettleForeverLonger() {
        assertEquals(880, cardStackSettleDurationMillis(baseDurationMillis = 220, settleStepCount = 4))
        assertEquals(880, cardStackSettleDurationMillis(baseDurationMillis = 220, settleStepCount = 40))
    }

    @Test
    fun reflowAndEnterTimingIgnoreStepCountEntirely() {
        val spec =
            com.riffle.core.domain.launcher.cards.CardStackAnimationSpec(
                durationMillis = 310,
                enterDurationMillis = 180,
                settleDurationMillis = 220,
            )

        assertEquals(180, cardStackAnimationDuration(spec, CardStackAnimationTiming.ENTER, settleStepCount = 5))
        assertEquals(310, cardStackAnimationDuration(spec, CardStackAnimationTiming.REFLOW, settleStepCount = 5))
        assertEquals(660, cardStackAnimationDuration(spec, CardStackAnimationTiming.SETTLE, settleStepCount = 3))
    }
}
