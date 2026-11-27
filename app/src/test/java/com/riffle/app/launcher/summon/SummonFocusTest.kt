package com.riffle.app.launcher.summon

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummonFocusTest {
    @Test
    fun aFocusedIconGrowsAndGlowsBrighter() {
        val rest = summonFocusLook(focused = false, reducedMotion = false)
        val focused = summonFocusLook(focused = true, reducedMotion = false)
        assertEquals(1f, rest.scale)
        assertEquals(SUMMON_FOCUSED_SCALE, focused.scale)
        assertTrue(focused.glow > rest.glow)
    }

    @Test
    fun reducedMotionKeepsTheSizeAndOnlyChangesTheGlow() {
        val rest = summonFocusLook(focused = false, reducedMotion = true)
        val focused = summonFocusLook(focused = true, reducedMotion = true)
        assertEquals(1f, focused.scale)
        assertTrue(focused.glow > rest.glow)
    }

    @Test
    fun losingFocusSettlesMoreSlowlyThanGainingIt() {
        assertTrue(summonFocusMillis(gaining = false) > summonFocusMillis(gaining = true))
    }

    @Test
    fun theGlowIsTheIconsOwnColourOrNeutralWhileItIsUnknown() {
        val pink = Color(0xFFEB5A8C)
        assertEquals(pink, summonGlowColor(pink))
        assertEquals(Color.White, summonGlowColor(null))
    }
}
