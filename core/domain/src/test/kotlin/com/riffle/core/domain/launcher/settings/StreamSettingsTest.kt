package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.designsystem.CardTint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StreamSettingsTest {
    @Test
    fun theDefaultsAreTheOnesTheDesignNames() {
        val defaults = LauncherSettings().stream

        assertEquals(22, defaults.cardTintPercent)
        assertFalse(defaults.cardTopGlow)
        assertEquals(OpenCardPlacement.CENTRE, defaults.openCardPlacement)
        assertEquals(CardActionsPlacement.IN_DOCK, defaults.cardActionsPlacement)
    }

    @Test
    fun theTintIsHeldToItsRangeAndTheRestIsKept() {
        val coerced = StreamSettings(cardTintPercent = 99, cardTopGlow = true).coerced()

        assertEquals(CardTint.MAX_STRENGTH_PERCENT, coerced.cardTintPercent)
        assertEquals(true, coerced.cardTopGlow)
        assertEquals(CardTint.MIN_STRENGTH_PERCENT, StreamSettings(cardTintPercent = -3).coerced().cardTintPercent)
    }
}
