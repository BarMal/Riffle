package com.riffle.app.launcher

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeDragGhostsTest {
    private val identity = AppIdentity(AppPackageName("pkg.ghost"), AppActivityName("Main"))

    /** A 6x6 icon: a red 4x4 square in the middle on a transparent ground. */
    private fun icon() =
        Bitmap.createBitmap(6, 6, Bitmap.Config.ARGB_8888).apply {
            for (x in 1..4) for (y in 1..4) setPixel(x, y, Color.RED)
        }.asImageBitmap()

    @Test
    fun theSourceGhostIsGreyAndDimmedWithTheSameSilhouette() {
        val ghost = HomeDragGhosts.bakeSource(icon()).asAndroidBitmap()
        val centre = ghost.getPixel(2, 2)
        assertEquals(Color.red(centre), Color.green(centre))
        assertEquals(Color.green(centre), Color.blue(centre))
        assertTrue(Color.alpha(centre) in 1..200)
        assertEquals(0, Color.alpha(ghost.getPixel(0, 0)))
    }

    @Test
    fun theDestinationGhostIsTheOutlineOfTheAlphaOnly() {
        val outline = HomeDragGhosts.bakeOutline(icon()).asAndroidBitmap()
        assertTrue(Color.alpha(outline.getPixel(1, 1)) > 0)
        assertTrue(Color.alpha(outline.getPixel(4, 2)) > 0)
        assertEquals(0, Color.alpha(outline.getPixel(2, 2)))
        assertEquals(0, Color.alpha(outline.getPixel(0, 0)))
    }

    @Test
    fun aGhostIsBakedOncePerIconAndTheCacheIsBounded() {
        HomeDragGhosts.clear()
        val first = HomeDragGhosts.forIcon(identity, icon())
        assertSame(first, HomeDragGhosts.forIcon(identity, icon()))
        repeat(200) {
            HomeDragGhosts.forIcon(AppIdentity(AppPackageName("pkg.$it"), AppActivityName("Main")), icon())
        }
        assertTrue(HomeDragGhosts.cachedCount <= 64)
    }
}
