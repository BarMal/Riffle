package com.riffle.app.launcher

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The edge lights never stroke a malformed outline, whatever size or shape they are given. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class GlassEdgeOutlineTest {
    private val density = Density(1f)

    private fun outline(
        shape: Shape,
        size: Size,
    ) = safeEdgeOutline(shape, size, LayoutDirection.Ltr, density)

    @Test
    fun aSurfaceWithoutAreaIsNotDrawable() {
        assertFalse(Size(0f, 0f).isDrawable())
        assertFalse(Size(0.4f, 100f).isDrawable())
        assertFalse(Size(100f, -3f).isDrawable())
        assertFalse(Size(Float.NaN, 10f).isDrawable())
        assertFalse(Size(Float.POSITIVE_INFINITY, 10f).isDrawable())
        assertTrue(Size(1f, 1f).isDrawable())
        assertTrue(Size(600f, 2f).isDrawable())
    }

    @Test
    fun aRoundedOutlineHasItsRadiiClampedToHalfTheShortestSide() {
        val size = Size(40f, 10f)
        val result = outline(RoundedCornerShape(500.dp), size) as Outline.Rounded
        listOf(
            result.roundRect.topLeftCornerRadius,
            result.roundRect.topRightCornerRadius,
            result.roundRect.bottomLeftCornerRadius,
            result.roundRect.bottomRightCornerRadius,
        ).forEach { radius ->
            assertTrue(radius.x <= 5f + 1e-3f && radius.y <= 5f + 1e-3f)
            assertTrue(radius.x.isFinite() && radius.y.isFinite())
        }
    }

    @Test
    fun aRadiusLargerThanTheSurfaceIsClampedOnAMalformedRoundedOutline() {
        val shape =
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Outline =
                    Outline.Rounded(
                        RoundRect(0f, 0f, size.width, size.height, CornerRadius(900f, Float.NaN)),
                    )
            }
        val result = outline(shape, Size(20f, 20f)) as Outline.Rounded
        assertEquals(10f, result.roundRect.topLeftCornerRadius.x, 1e-3f)
        assertEquals(0f, result.roundRect.topLeftCornerRadius.y, 1e-3f)
    }

    @Test
    fun anOutlineThatIsEmptyOrNotFiniteFallsBackToTheRectangle() {
        val empty =
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Outline = Outline.Generic(Path())
            }
        val infinite =
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density,
                ): Outline = Outline.Rectangle(Rect(0f, 0f, Float.POSITIVE_INFINITY, 5f))
            }
        assertTrue(outline(empty, Size(30f, 30f)) is Outline.Rectangle)
        assertEquals(Rect(0f, 0f, 30f, 30f), (outline(infinite, Size(30f, 30f)) as Outline.Rectangle).rect)
    }

    @Test
    fun theBorderRampIsSolidBandsThatTileTheHeightAndFadeTowardTheBottom() {
        val top = Color.White.copy(alpha = 0.55f)
        val side = Color.White.copy(alpha = 0.14f)
        val bottom = Color.White.copy(alpha = 0.06f)
        val bands = borderBands(listOf(0f to top, 0.1f to side, 0.9f to side, 1f to bottom), height = 200f)

        // Two changing stretches of four bands each, and one flat stretch that is a single band.
        assertEquals(9, bands.size)
        assertEquals(0f, bands.first().top, 0.001f)
        assertEquals(200f, bands.last().bottom, 0.001f)
        bands.zipWithNext().forEach { (above, below) -> assertEquals(above.bottom, below.top, 0.001f) }
        bands.zipWithNext().forEach { (above, below) -> assertTrue(above.color.alpha >= below.color.alpha) }
        assertTrue(bands.first().color.alpha > side.alpha && bands.first().color.alpha < top.alpha)
        assertEquals(side.alpha, bands[4].color.alpha, 0.001f)
    }

    @Test
    fun aBorderRampWithNothingToDrawHasNoBands() {
        assertTrue(borderBands(emptyList(), height = 100f).isEmpty())
        assertTrue(borderBands(listOf(0f to Color.White, 1f to Color.White), height = 0f).isEmpty())
        assertTrue(borderBands(listOf(0f to Color.White, 1f to Color.White), height = Float.NaN).isEmpty())
        // Stops that rounding left on top of each other make no band of zero height.
        assertEquals(1, borderBands(listOf(0f to Color.White, 0f to Color.Black, 1f to Color.Black), 50f).size)
    }

    @Test
    fun everyShapeTheAppDrawsGlassInSurvivesEverySize() {
        val shapes =
            listOf(
                RectangleShape,
                CircleShape,
                RoundedCornerShape(0.dp),
                RoundedCornerShape(1000.dp),
                RiffleShapes.small,
                RiffleShapes.medium,
                RiffleShapes.large,
                RiffleShapes.full,
            )
        val sizes =
            listOf(
                Size(1f, 1f),
                Size(2f, 2f),
                Size(1f, 900f),
                Size(900f, 1f),
                Size(900f, 3f),
                Size(48f, 48f),
                Size(1080f, 160f),
            )
        shapes.forEach { shape -> sizes.forEach { size -> assertUsable(shape, size) } }
    }

    private fun assertUsable(
        shape: Shape,
        size: Size,
    ) {
        val bounds =
            when (val result = outline(shape, size)) {
                is Outline.Rectangle -> result.rect
                is Outline.Rounded -> result.roundRect.let { Rect(it.left, it.top, it.right, it.bottom) }
                is Outline.Generic -> result.path.getBounds()
            }
        assertTrue("$shape $size -> $bounds", bounds.width.isFinite() && bounds.height.isFinite())
        assertTrue("$shape $size -> $bounds", bounds.width >= 1f && bounds.height >= 1f)
    }
}
