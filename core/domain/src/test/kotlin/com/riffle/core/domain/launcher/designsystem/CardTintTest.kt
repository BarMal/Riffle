package com.riffle.core.domain.launcher.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CardTintTest {
    /** Very dark, very light, saturated and grey app colours, and the ones in between where text is hardest. */
    private val spread =
        listOf(
            "near black" to 0x0A0A0A,
            "very dark blue" to 0x0B1F4D,
            "dark green" to 0x0E5A2B,
            "saturated red" to 0xE53935,
            "saturated green" to 0x25D366,
            "saturated blue" to 0x1877F2,
            "saturated yellow" to 0xFFD60A,
            "orange" to 0xFF6D00,
            "magenta" to 0xE91E63,
            "teal" to 0x009688,
            "mid grey" to 0x808080,
            "light grey" to 0xCCCCCC,
            "very light yellow" to 0xFFF9C4,
            "white" to 0xFFFFFF,
        )

    private fun channels(rgb: Int) =
        Triple((rgb shr 16 and 0xFF) / 255f, (rgb shr 8 and 0xFF) / 255f, (rgb and 0xFF) / 255f)

    /** The contrast a viewer gets of [fill]'s text, with the fill composited over a flat [backdrop]. */
    private fun contrastOver(
        fill: CardTint.CardFill,
        backdrop: Float,
    ): Float {
        val shown = GlassBlend.flatOver(channels(fill.rgb), fill.alpha, Triple(backdrop, backdrop, backdrop))
        val ink = channels(fill.inkRgb)
        return GlassBlend.contrast(
            GlassBlend.luminance(ink.first, ink.second, ink.third),
            GlassBlend.luminance(shown.first, shown.second, shown.third),
        )
    }

    /** Every (theme, strength, app colour) the spread covers, with the card fill it gets. */
    private fun fills(strengths: List<Int>) =
        listOf(true, false).flatMap { darkTheme ->
            strengths.flatMap { strength ->
                spread.map { (name, rgb) ->
                    Triple("$name at $strength% (dark=$darkTheme)", CardTint.fill(rgb, strength, darkTheme), rgb)
                }
            }
        }

    @Test
    fun bodyTextKeepsFourAndAHalfToOneOnEveryAppColourAtTheDefaultAndTheMaximumStrength() {
        val strengths = listOf(CardTint.DEFAULT_STRENGTH_PERCENT, CardTint.MAX_STRENGTH_PERCENT)
        for ((label, fill, _) in fills(strengths)) {
            for (backdrop in listOf(0.5f, 0f, 1f)) {
                val contrast = contrastOver(fill, backdrop)
                assertTrue(
                    contrast >= GlassBlend.MINIMUM_TEXT_CONTRAST,
                    "$label over $backdrop: $contrast, alpha ${fill.alpha}",
                )
            }
        }
    }

    @Test
    fun aCardWithNoAppColourAndEveryStrengthIsReadableToo() {
        val strengths = 0..CardTint.MAX_STRENGTH_PERCENT step CardTint.STRENGTH_STEP_PERCENT
        val fills = listOf(true, false).flatMap { dark -> strengths.map { CardTint.fill(null, it, dark) } }
        for (fill in fills) {
            CardTint.BACKDROPS.forEach { backdrop ->
                assertTrue(contrastOver(fill, backdrop) >= GlassBlend.MINIMUM_TEXT_CONTRAST)
            }
        }
    }

    @Test
    fun reducedTransparencyStaysNearOpaqueAndReadable() {
        val fills =
            listOf(true, false).flatMap { dark ->
                spread.map { (_, rgb) ->
                    CardTint.fill(rgb, CardTint.MAX_STRENGTH_PERCENT, dark, reducedTransparency = true)
                }
            }
        for (fill in fills) {
            assertEquals(GlassMaterialTokens.REDUCED_TRANSPARENCY_FILL_ALPHA, fill.alpha)
            CardTint.BACKDROPS.forEach { backdrop ->
                assertTrue(contrastOver(fill, backdrop) >= GlassBlend.MINIMUM_TEXT_CONTRAST - 0.5f)
            }
        }
    }

    @Test
    fun theTintIsThatShareOfTheAppColourOverTheWeightsFillColour() {
        val base = CardTint.DARK_CARD_BASE_RGB
        val expected = GlassMaterialTokens.mixRgb(base, 0x25D366, 0.22f)
        // A green this saturated needs no push, so the colour is the plain mix.
        assertEquals(expected, CardTint.fill(0x25D366, 22, darkTheme = true).rgb)
        assertEquals(base, CardTint.fill(0x25D366, 0, darkTheme = true).rgb)
        assertEquals(base, CardTint.fill(null, 22, darkTheme = true).rgb)
    }

    @Test
    fun aStrongerTintMovesTheFillTowardTheAppColour() {
        val weak = CardTint.fill(0x1877F2, 10, darkTheme = true).rgb
        val strong = CardTint.fill(0x1877F2, 40, darkTheme = true).rgb
        assertNotEquals(weak, strong)
        // The blue channel of the app colour is far above the indigo base's: more tint, more blue.
        assertTrue((strong and 0xFF) > (weak and 0xFF))
    }

    @Test
    fun theFillIsNeverLessOpaqueThanRegularAndStaysTranslucent() {
        for ((label, fill, _) in fills(listOf(CardTint.DEFAULT_STRENGTH_PERCENT))) {
            assertTrue(fill.alpha >= GlassMaterialTokens.regular.fillAlpha, label)
            assertTrue(fill.alpha < 1f, "$label ${fill.alpha}")
        }
    }

    @Test
    fun aPaleAppColourOnTheDarkThemeStillGetsReadableText() {
        val fill = CardTint.fill(0xFFF9C4, CardTint.MAX_STRENGTH_PERCENT, darkTheme = true)
        CardTint.BACKDROPS.forEach { backdrop ->
            assertTrue(contrastOver(fill, backdrop) >= GlassBlend.MINIMUM_TEXT_CONTRAST)
        }
    }

    @Test
    fun strengthIsHeldToItsRange() {
        assertEquals(CardTint.MAX_STRENGTH_PERCENT, CardTint.coercedStrength(500))
        assertEquals(CardTint.MIN_STRENGTH_PERCENT, CardTint.coercedStrength(-5))
        assertEquals(22, CardTint.coercedStrength(22))
    }
}
