package com.riffle.app.launcher.stream

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class StreamAvatarPlanTest {
    private fun plan(
        person: Boolean,
        photo: Boolean = false,
        initial: Boolean = true,
        appIcon: Boolean = true,
        photoIsAppIcon: Boolean = false,
    ) = AvatarPlanner.plan(person, photo, initial, appIcon, photoIsAppIcon)

    @Test
    fun aNotificationWithNoPersonIsTheAppsIconWithNoInitialsAndNoBadge() {
        // A shopping app's promotion: its title starts with a P, but nobody sent it.
        assertEquals(AvatarPlan(AvatarFace.APP_ICON, badge = false), plan(person = false, initial = true))
        assertEquals(AvatarPlan(AvatarFace.APP_ICON, badge = false), plan(person = false, photo = true))
    }

    @Test
    fun aPersonIsTheirPhotoElseTheirInitialsWithTheAppsIconBadged() {
        assertEquals(AvatarPlan(AvatarFace.PHOTO, badge = true), plan(person = true, photo = true))
        assertEquals(AvatarPlan(AvatarFace.INITIALS, badge = true), plan(person = true, photo = false))
    }

    @Test
    fun theBadgeIsNeverDrawnOnTheAppsOwnIcon() {
        assertEquals(AvatarPlan(AvatarFace.APP_ICON, badge = false), plan(person = true, initial = false))
        // A large icon that is the app's own logo is the app's icon: no second copy of it at the corner.
        assertEquals(
            AvatarPlan(AvatarFace.PHOTO, badge = false),
            plan(person = true, photo = true, photoIsAppIcon = true),
        )
        // No icon to badge, no badge.
        assertEquals(AvatarPlan(AvatarFace.INITIALS, badge = false), plan(person = true, appIcon = false))
    }

    private fun solid(
        color: Int,
        size: Int = 48,
    ): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }

    @Test
    fun theSameLogoAtAnotherSizeIsTheSameIconAndADifferentPictureIsNot() {
        assertTrue(IconLookalike.same(solid(Color.rgb(200, 40, 40), 48), solid(Color.rgb(205, 38, 42), 96)))
        assertFalse(IconLookalike.same(solid(Color.rgb(200, 40, 40)), solid(Color.rgb(40, 40, 200))))
        assertFalse(IconLookalike.same(null, null))
    }

    @Test
    fun aPictureIsAsTallAsItsProportionsMakeItUpToTheCap() {
        // 400 dp wide, a 2:1 picture is 200 dp tall; a tall one is capped and cropped, never squeezed.
        assertEquals(200.dp, PictureLayout.heightFor(400.dp, 1000, 500, 280.dp))
        assertEquals(280.dp, PictureLayout.heightFor(400.dp, 500, 1000, 280.dp))
        assertEquals(0.dp, PictureLayout.heightFor(400.dp, 0, 0, 280.dp))
    }
}
