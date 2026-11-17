package com.riffle.app.launcher.pair

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fused pair icon, pixel by pixel on small synthetic icons: solid red (first) and solid blue (second). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PairIconComposerTest {
    private fun solid(color: Int): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    private val red = solid(Color.RED)
    private val blue = solid(Color.BLUE)

    private fun Bitmap.at(x: Int) = getPixel(x, SIZE / 2)

    @Test
    fun theLeftEdgeIsTheFirstAppsColourAndTheRightEdgeTheSeconds() {
        val fused = PairIconComposer.compose(red, blue, SIZE, rtl = false)

        assertEquals(Color.RED, fused.at(0))
        assertEquals(Color.BLUE, fused.at(SIZE - 1))
        assertEquals(Color.RED, fused.at(SIZE * 3 / 10))
        assertEquals(Color.BLUE, fused.at(SIZE * 7 / 10))
    }

    @Test
    fun theCentreIsABlendOfBothAndOpaque() {
        val centre = PairIconComposer.compose(red, blue, SIZE, rtl = false).at(SIZE / 2)

        assertEquals(255, Color.alpha(centre))
        assertTrue("no red in the centre", Color.red(centre) in 100..155)
        assertTrue("no blue in the centre", Color.blue(centre) in 100..155)
        assertEquals(0, Color.green(centre))
    }

    @Test
    fun theFadeGoesFromTheFirstToTheSecondAcrossTheMiddleOnly() {
        val fused = PairIconComposer.compose(red, blue, SIZE, rtl = false)
        val reds = (0 until SIZE).map { Color.red(fused.at(it)) }

        reds.zipWithNext().forEach { (a, b) -> assertTrue("red rose again: $reds", b <= a + 1) }
        val fadeStart = (SIZE * 0.38f).toInt()
        val fadeEnd = (SIZE * 0.62f).toInt()
        assertTrue((0 until fadeStart - 1).all { reds[it] == 255 })
        assertTrue((fadeEnd + 1 until SIZE).all { reds[it] == 0 })
    }

    @Test
    fun aRightToLeftLayoutMirrorsTheOrder() {
        val ltr = PairIconComposer.compose(red, blue, SIZE, rtl = false)
        val rtl = PairIconComposer.compose(red, blue, SIZE, rtl = true)

        assertEquals(Color.BLUE, rtl.at(0))
        assertEquals(Color.RED, rtl.at(SIZE - 1))
        (0 until SIZE).forEach { x ->
            val a = ltr.at(SIZE - 1 - x)
            val b = rtl.at(x)
            val close =
                kotlin.math.abs(Color.red(a) - Color.red(b)) <= 2 && kotlin.math.abs(Color.blue(a) - Color.blue(b)) <= 2
            assertTrue("column $x is not mirrored", close)
        }
    }

    @Test
    fun swappingTheAppsChangesTheTile() {
        val a = PairIconComposer.compose(red, blue, SIZE, rtl = false)
        val b = PairIconComposer.compose(blue, red, SIZE, rtl = false)

        assertEquals(Color.BLUE, b.at(0))
        assertNotEquals(a.at(0), b.at(0))
    }

    @Test
    fun aTransparentIconCornerStaysTransparentWhereNeitherAppDraws() {
        val first =
            Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { it.setPixel(SIZE / 2, SIZE / 2, Color.RED) }
        val fused = PairIconComposer.compose(first, blue, SIZE, rtl = false)

        assertEquals(0, Color.alpha(fused.getPixel(1, 1)))
        assertEquals(Color.BLUE, fused.getPixel(SIZE - 1, 1))
    }

    @Test
    fun theCacheReturnsTheSameInstanceForTheSameKeyAndMakesOneOnlyOnce() {
        val cache = PairIconCache(maxEntries = 4)
        val key = PairIconKey(identity("a"), identity("b"), SIZE, rtl = false)
        var made = 0
        val make = {
            made++
            PairIconComposer.compose(red, blue, SIZE, rtl = false).asImageBitmap()
        }

        val one = cache.getOrCompose(key, make)
        val two = cache.getOrCompose(key, make)

        assertSame(one, two)
        assertEquals(1, made)
        assertNotSame(one, cache.getOrCompose(key.copy(second = identity("a"), first = identity("b")), make))
        assertNotSame(one, cache.getOrCompose(key.copy(rtl = true), make))
        assertNotSame(one, cache.getOrCompose(key.copy(sizePx = SIZE + 8), make))
    }

    @Test
    fun theCacheIsBounded() {
        val cache = PairIconCache(maxEntries = 2)
        val make = { PairIconComposer.compose(red, blue, SIZE, rtl = false).asImageBitmap() }
        val keys = (0 until 5).map { PairIconKey(identity("a$it"), identity("b"), SIZE, rtl = false) }

        keys.forEach { cache.getOrCompose(it, make) }

        assertEquals(null, cache.cached(keys.first()))
        assertTrue(cache.cached(keys.last()) != null)
    }

    private fun identity(name: String) = AppIdentity(AppPackageName("com.riffle.$name"), AppActivityName(".Main"))

    private companion object {
        const val SIZE = 40
    }
}
