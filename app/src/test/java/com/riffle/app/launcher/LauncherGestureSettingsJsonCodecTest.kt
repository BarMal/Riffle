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
import org.junit.Assert.assertEquals
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
}
