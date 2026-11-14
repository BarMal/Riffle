package com.riffle.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Color as AndroidColor

/**
 * The glass is drawn at sizes no design asks for -- 0 x 0, one pixel, a hairline-thin strip, a
 * radius far larger than the surface -- and nothing throws or draws garbage. (A device test run once
 * lost its whole process around the Dock's small, heavily rounded tiles; whether or not that was the
 * glass, the glass must be defensively correct at degenerate sizes.) The native graphics mode draws
 * with Skia, so the gradient strokes really are rasterised here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GlassDegenerateSizeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyWeightDrawsAtEveryDegenerateSize() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Row {
                    GlassWeight.entries.forEach { weight -> DegenerateSurfaces(weight) }
                }
            }
        }
        composeRule.waitForIdle()
        val image = drawWindow()
        assertTrue(image.width > 0 && image.height > 0)
        // It really drew: the backdrop's wallpaper gradient is in the bitmap.
        assertTrue(AndroidColor.alpha(image.getPixel(image.width / 2, image.height - 2)) > 0)
    }

    @Test
    fun surfacesCarryingAnEdgeTintDrawAtDegenerateSizes() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Row {
                    listOf(0.dp, 1.dp, 3.dp, 6.dp, 48.dp).forEach { side ->
                        GlassSurface(
                            modifier = Modifier.size(side, side),
                            weight = GlassWeight.Regular,
                            shape = RoundedCornerShape(100.dp),
                            edgeTint = Color(0xFFFF6A00),
                            spotlight = true,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        drawWindow()
    }

    @Test
    fun aSurfaceThatCollapsesAndGrowsBackDrawsEveryTimeThroughZero() {
        var side by mutableStateOf(48.dp)
        composeRule.setContent {
            ScreenshotBackdrop {
                GlassSurface(
                    modifier = Modifier.size(side),
                    weight = GlassWeight.Thin,
                    shape = RiffleShapes.small,
                )
            }
        }
        listOf(0.dp, 1.dp, 0.dp, 48.dp, 2.dp).forEach { next ->
            composeRule.runOnIdle { side = next }
            composeRule.waitForIdle()
            drawWindow()
        }
    }

    /** Draws the whole window into a bitmap, which runs every node's draw (Skia, in the native graphics mode). */
    private fun drawWindow(): Bitmap {
        val decor = composeRule.activity.window.decorView
        val bitmap =
            Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        decor.draw(Canvas(bitmap))
        return bitmap
    }

    @Composable
    private fun DegenerateSurfaces(weight: GlassWeight) {
        val shapes =
            listOf(
                RoundedCornerShape(0.dp),
                RoundedCornerShape(500.dp),
                CircleShape,
                RiffleShapes.small,
                RiffleShapes.full,
            )
        val sizes =
            listOf(
                0.dp to 0.dp,
                1.dp to 1.dp,
                0.5.dp to 0.5.dp,
                2.dp to 2.dp,
                300.dp to 1.dp,
                300.dp to 3.dp,
                1.dp to 300.dp,
                48.dp to 48.dp,
            )
        Box(Modifier.fillMaxSize()) {
            shapes.forEach { shape ->
                sizes.forEach { (w, h) -> Cell(weight, shape, w, h) }
            }
        }
    }

    @Composable
    private fun Cell(
        weight: GlassWeight,
        shape: androidx.compose.ui.graphics.Shape,
        w: Dp,
        h: Dp,
    ) {
        GlassSurface(modifier = Modifier.width(w).height(h), weight = weight, shape = shape)
    }
}
