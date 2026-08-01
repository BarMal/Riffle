package com.riffle.app.launcher.apps

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconDominantColorTest {
    @Test
    fun solidSaturatedBitmapReturnsRoughlyThatHue() {
        val bitmap =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(220, 40, 40)) // strongly saturated red.
            }.asImageBitmap()

        val color = dominantColorOf(bitmap)

        assertNotNull(color)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color!!.toArgb(), hsv)
        // Pure red is hue 0; allow slack for the fixed-bucket quantization.
        assertTrue("expected a red-ish hue but was ${hsv[0]}", hsv[0] < 20f || hsv[0] > 340f)
    }

    @Test
    fun fullyTransparentBitmapReturnsNull() {
        val bitmap =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.TRANSPARENT)
            }.asImageBitmap()

        assertNull(dominantColorOf(bitmap))
    }

    @Test
    fun grayBitmapWithNoSaturatedPixelsReturnsNull() {
        val bitmap =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(128, 128, 128))
            }.asImageBitmap()

        assertNull(dominantColorOf(bitmap))
    }

    @Test
    fun mixedSaturatedAndDesaturatedPixelsPrefersTheSaturatedRegion() {
        val bitmap =
            Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        // Most of the icon is a near-gray background; a small strongly-saturated
                        // blue region should still dominate the weighted hue vote.
                        val color =
                            if (x < width / 4 && y < height / 4) {
                                android.graphics.Color.rgb(20, 40, 230)
                            } else {
                                android.graphics.Color.rgb(210, 205, 200)
                            }
                        setPixel(x, y, color)
                    }
                }
            }.asImageBitmap()

        val color = dominantColorOf(bitmap, strideX = 1, strideY = 1)

        assertNotNull(color)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color!!.toArgb(), hsv)
        // Blue sits around hue 225-240; allow slack for the fixed-bucket quantization.
        assertTrue("expected a blue-ish hue but was ${hsv[0]}", hsv[0] in 190f..270f)
    }

    @Test
    fun degenerateOnePixelBitmapNeverThrows() {
        val bitmap =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                setPixel(0, 0, android.graphics.Color.rgb(200, 30, 30))
            }.asImageBitmap()

        // Should not throw regardless of the outcome.
        dominantColorOf(bitmap)
    }
}
