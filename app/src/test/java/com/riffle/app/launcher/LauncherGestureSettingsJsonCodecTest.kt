package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LauncherGestureSettingsJsonCodecTest {
    @Test
    fun roundTripsAppAndShortcutGestureTargets() {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.riffle.mail"),
                activityName = AppActivityName("com.riffle.mail.MainActivity"),
                profile = AppProfile.work(),
            )
        val shortcut =
            AppShortcut(
                id = AppShortcutId("compose"),
                appIdentity = appIdentity,
                shortLabel = "Compose",
                longLabel = "Compose message",
            )
        val settings =
            GestureSettings(
                homeGestures =
                    HomeGestureSettings(
                        actions =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureAction.LAUNCH_APP,
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureAction.LAUNCH_APP_SHORTCUT,
                            ),
                        launchTargets =
                            mapOf(
                                HomeGesture.TWO_FINGER_LEFT to LauncherGestureLaunchTarget.App(appIdentity),
                                HomeGesture.TWO_FINGER_RIGHT to LauncherGestureLaunchTarget.Shortcut(shortcut),
                            ),
                    ),
            )

        val decoded = encodeGestures(settings).toGestures(GestureSettings())

        assertEquals(
            LauncherGestureLaunchTarget.App(appIdentity),
            decoded.homeGestures.launchTargetFor(HomeGesture.TWO_FINGER_LEFT),
        )
        assertEquals(
            LauncherGestureLaunchTarget.Shortcut(shortcut),
            decoded.homeGestures.launchTargetFor(HomeGesture.TWO_FINGER_RIGHT),
        )
    }

    @Test
    fun decodesRemovedModeActionsAsNoAction() {
        // Written by builds that bound mode switching to gestures: before the mode ring (#1225) as
        // Enter/Exit Cards, after it as next/previous mode. Both were removed when the dock pull
        // became the only mode-transition trigger.
        val stored =
            JSONObject()
                .put(
                    "homeGestures",
                    JSONObject()
                        .put(HomeGesture.THREE_FINGER_UP.name, "NEXT_MODE")
                        .put(HomeGesture.THREE_FINGER_DOWN.name, "PREVIOUS_MODE")
                        .put(HomeGesture.TWO_FINGER_LEFT.name, "ENTER_ADAPTIVE_STAGE")
                        .put(HomeGesture.TWO_FINGER_RIGHT.name, "EXIT_ADAPTIVE_STAGE")
                        // Bound away from its default, so "no action" cannot be the default showing through.
                        .put(HomeGesture.ONE_FINGER_LEFT.name, "NEXT_MODE")
                        .put(HomeGesture.ONE_FINGER_DOWN.name, LauncherGestureAction.OPEN_SEARCH.name),
                )

        val decoded = stored.toGestures(GestureSettings()).homeGestures

        listOf(
            HomeGesture.THREE_FINGER_UP,
            HomeGesture.THREE_FINGER_DOWN,
            HomeGesture.TWO_FINGER_LEFT,
            HomeGesture.TWO_FINGER_RIGHT,
            HomeGesture.ONE_FINGER_LEFT,
        ).forEach { gesture ->
            assertEquals(gesture.name, LauncherGestureAction.NONE, decoded.actionFor(gesture))
        }
        assertEquals(LauncherGestureAction.OPEN_SEARCH, decoded.actionFor(HomeGesture.ONE_FINGER_DOWN))
    }

    @Test
    fun decodesRemovedModeActionsInTheLegacyHomeSwipeShapeAsNoAction() {
        val stored =
            JSONObject().put(
                "homeSwipe",
                JSONObject()
                    .put("up", "PREVIOUS_MODE")
                    .put("left", "EXIT_ADAPTIVE_STAGE")
                    .put("down", LauncherGestureAction.OPEN_SETTINGS.name),
            )

        val decoded = stored.toGestures(GestureSettings()).homeGestures

        assertEquals(LauncherGestureAction.NONE, decoded.actionFor(HomeGesture.ONE_FINGER_UP))
        assertEquals(LauncherGestureAction.NONE, decoded.actionFor(HomeGesture.ONE_FINGER_LEFT))
        assertEquals(LauncherGestureAction.OPEN_SETTINGS, decoded.actionFor(HomeGesture.ONE_FINGER_DOWN))
    }

    @Test
    fun missingOrBlankActionsStillFallBackToDefaults() {
        val stored =
            JSONObject().put(
                "homeGestures",
                JSONObject().put(HomeGesture.ONE_FINGER_DOWN.name, ""),
            )

        val decoded = stored.toGestures(GestureSettings()).homeGestures

        assertEquals(LauncherGestureAction.OPEN_NOTIFICATIONS, decoded.actionFor(HomeGesture.ONE_FINGER_DOWN))
        assertEquals(LauncherGestureAction.SELECT_NEXT_HOME_PAGE, decoded.actionFor(HomeGesture.ONE_FINGER_LEFT))
    }

    @Test
    fun ignoresTheRemovedDockSwipeUpBinding() {
        // The dock swipe-up gesture and its setting were removed; stored values must not break decoding.
        listOf("PREVIOUS_MODE", "OPEN_APP_DRAWER", "EXIT_ADAPTIVE_STAGE", "NONE").forEach { stored ->
            val decoded =
                JSONObject()
                    .put("homeGestures", JSONObject().put(HomeGesture.TWO_FINGER_UP.name, "OPEN_SEARCH"))
                    .put("dockGestures", JSONObject().put("swipeUp", stored))
                    .toGestures(GestureSettings())

            assertEquals(LauncherGestureAction.OPEN_SEARCH, decoded.homeGestures.actionFor(HomeGesture.TWO_FINGER_UP))
        }
    }

    @Test
    fun noLongerWritesDockGestures() {
        assertFalse(encodeGestures(GestureSettings()).has("dockGestures"))
    }
}
