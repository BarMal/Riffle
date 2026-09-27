package com.riffle.app.launcher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure decisions behind the dock-to-header identity transition (see
 * AdaptiveStageHeaderIdentityTransition.kt's own doc): whether one should run at all, and the
 * coordinate math that turns a dock icon's root-coordinate tap bounds into the header's own local
 * space. Everything else in that file is Compose animation plumbing, not independently testable
 * without a device.
 */
class AdaptiveStageHeaderIdentityTransitionTest {
    @Test
    fun animatesWhenTheTappedIdentityIsTheOneNowShown() {
        assertTrue(
            shouldAnimateIdentityTransition(
                request = request(mailApp),
                shownIdentity = mailApp,
                reducedMotion = false,
            ),
        )
    }

    @Test
    fun neverAnimatesUnderReducedMotion() {
        assertFalse(
            shouldAnimateIdentityTransition(
                request = request(mailApp),
                shownIdentity = mailApp,
                reducedMotion = true,
            ),
        )
    }

    @Test
    fun neverAnimatesWithoutARequest() {
        assertFalse(shouldAnimateIdentityTransition(request = null, shownIdentity = mailApp, reducedMotion = false))
    }

    @Test
    fun neverAnimatesWithNothingShown() {
        assertFalse(
            shouldAnimateIdentityTransition(request = request(mailApp), shownIdentity = null, reducedMotion = false),
        )
    }

    @Test
    fun neverAnimatesADifferentIdentityThanTheOneRequested() {
        // A dock tap for one app landing while a swipe already moved the header on to another --
        // the header must not fly the wrong icon in.
        assertFalse(
            shouldAnimateIdentityTransition(
                request = request(mailApp),
                shownIdentity = notesApp,
                reducedMotion = false,
            ),
        )
    }

    @Test
    fun relativeToShiftsARectByTheGivenOrigin() {
        val rect = Rect(left = 100f, top = 200f, right = 148f, bottom = 248f)
        val relative = rect.relativeTo(Offset(x = 20f, y = 30f))
        assertEquals(Rect(left = 80f, top = 170f, right = 128f, bottom = 218f), relative)
    }

    @Test
    fun relativeToAZeroOriginIsUnchanged() {
        val rect = Rect(left = 10f, top = 10f, right = 50f, bottom = 50f)
        assertEquals(rect, rect.relativeTo(Offset.Zero))
    }

    private fun request(identity: AppIdentity) =
        DockIdentityTransitionRequest(identity = identity, sourceBounds = Rect.Zero, requestId = 1L)

    private companion object {
        private val mailApp =
            AppIdentity(
                packageName = AppPackageName("com.riffle.mail"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )
        private val notesApp =
            AppIdentity(
                packageName = AppPackageName("com.riffle.notes"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )
    }
}
