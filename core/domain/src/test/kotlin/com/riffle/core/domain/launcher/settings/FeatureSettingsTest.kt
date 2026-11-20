package com.riffle.core.domain.launcher.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FeatureSettingsTest {
    @Test
    fun everySwitchDefaultsToTodaysBehaviourAndSimpleModeIsOff() {
        val features = FeatureSettings()
        assertTrue(features.stream && features.dockLiveApps && features.library)
        assertFalse(features.simpleMode)
        assertEquals(HomeMode.PlacedItems, LauncherSettings().home.mode)
        assertEquals(EffectiveFeatures(), LauncherSettings().effectiveFeatures())
    }

    @Test
    fun homeModeStoredIdsAreStableAndUnknownOnesAreTheDefault() {
        assertEquals(listOf("placed_items", "app_library"), HomeMode.entries.map { it.storedId })
        HomeMode.entries.forEach { mode -> assertEquals(mode, HomeMode.decode(mode.storedId)) }
        assertEquals(HomeMode.PlacedItems, HomeMode.decode(null))
        assertEquals(HomeMode.PlacedItems, HomeMode.decode("carousel"))
        assertNull(HomeMode.fromStoredId("carousel"))
    }

    @Test
    fun eachSwitchTurnsOffOnlyItself() {
        val base = FeatureSettings(dockStreamHandle = true, streamViewButton = true)
        assertEquals(base.copy(stream = false), base.with(LauncherFeature.STREAM, false))
        assertEquals(base.copy(dockLiveApps = false), base.with(LauncherFeature.DOCK_LIVE_APPS, false))
        assertEquals(base.copy(library = false), base.with(LauncherFeature.LIBRARY, false))
        assertEquals(base.copy(dockStreamHandle = false), base.with(LauncherFeature.DOCK_STREAM_HANDLE, false))
        assertEquals(base.copy(streamViewButton = false), base.with(LauncherFeature.STREAM_VIEW_BUTTON, false))
        assertEquals(base, base.with(LauncherFeature.STREAM, false).with(LauncherFeature.STREAM, true))
    }

    @Test
    fun theStreamSwitchTakesTheLiveAppsWithIt() {
        val effective = FeatureSettings(stream = false).effective(HomeMode.PlacedItems)
        assertFalse(effective.stream)
        assertFalse(effective.dockLiveApps)
        assertTrue(effective.library)
    }

    @Test
    fun theVisibleCuesAreOffByDefaultOptInAndGoWithTheStreamAndSimpleMode() {
        val default = FeatureSettings().effective(HomeMode.PlacedItems)
        assertFalse(default.dockStreamHandle || default.streamViewButton)

        val optedIn = FeatureSettings(dockStreamHandle = true, streamViewButton = true)
        val on = optedIn.effective(HomeMode.PlacedItems)
        assertTrue(on.dockStreamHandle && on.streamViewButton)
        assertFalse(optedIn.copy(simpleMode = true).effective(HomeMode.PlacedItems).dockStreamHandle)

        // Without a Stream there is nothing for the handle to open and no page for the button.
        val noStream =
            FeatureSettings(stream = false, dockStreamHandle = true, streamViewButton = true)
                .effective(HomeMode.PlacedItems)
        assertFalse(noStream.dockStreamHandle || noStream.streamViewButton)
    }

    @Test
    fun simpleModeLeavesTheAppLibraryAndTheDockOnly() {
        val effective = FeatureSettings(simpleMode = true).effective(HomeMode.PlacedItems)
        assertEquals(
            EffectiveFeatures(
                stream = false,
                dockLiveApps = false,
                library = false,
                homeMode = HomeMode.AppLibrary,
                simpleMode = true,
                dockStreamHandle = false,
                streamViewButton = false,
            ),
            effective,
        )
    }

    @Test
    fun turningSimpleModeOffGivesBackTheStoredSwitchesAndHomeMode() {
        val stored = LauncherSettings(features = FeatureSettings(library = false), home = HomeBehaviourSettings())
        val simple = stored.copy(features = stored.features.copy(simpleMode = true))
        assertEquals(HomeMode.AppLibrary, simple.effectiveFeatures().homeMode)
        val back = simple.copy(features = simple.features.copy(simpleMode = false))
        assertEquals(stored.effectiveFeatures(), back.effectiveFeatures())
        assertEquals(HomeMode.PlacedItems, back.effectiveFeatures().homeMode)
        assertFalse(back.effectiveFeatures().library)
    }

    @Test
    fun aStoredHomeModeShowsWhenSimpleModeIsOff() {
        val settings = LauncherSettings(home = HomeBehaviourSettings(mode = HomeMode.AppLibrary))
        assertEquals(HomeMode.AppLibrary, settings.effectiveFeatures().homeMode)
    }

    @Test
    fun gesturesLeadingToASwitchedOffFeatureAreUnbound() {
        val gestures =
            HomeGestureSettings()
                .withAction(HomeGesture.TWO_FINGER_LEFT, LauncherGestureAction.OPEN_STREAM)
                .withAction(HomeGesture.TWO_FINGER_RIGHT, LauncherGestureAction.OPEN_LIBRARY)
        val on = EffectiveFeatures()
        assertSame(gestures, gestures.without(on))

        val noStream = gestures.without(EffectiveFeatures(stream = false))
        assertEquals(LauncherGestureAction.NONE, noStream.actionFor(HomeGesture.TWO_FINGER_LEFT))
        assertEquals(LauncherGestureAction.OPEN_LIBRARY, noStream.actionFor(HomeGesture.TWO_FINGER_RIGHT))

        val noLibrary = gestures.without(EffectiveFeatures(library = false))
        assertEquals(LauncherGestureAction.OPEN_STREAM, noLibrary.actionFor(HomeGesture.TWO_FINGER_LEFT))
        assertEquals(LauncherGestureAction.NONE, noLibrary.actionFor(HomeGesture.TWO_FINGER_RIGHT))
        // The other bindings are untouched.
        assertEquals(LauncherGestureAction.OPEN_SEARCH, noLibrary.actionFor(HomeGesture.ONE_FINGER_DOWN))
    }

    @Test
    fun appLibraryHomeDropsTheGesturesThatActOnPlacedPages() {
        val gestures = HomeGestureSettings().forAppLibraryHome()
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.ONE_FINGER_LEFT))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.ONE_FINGER_RIGHT))
        assertEquals(LauncherGestureAction.NONE, gestures.actionFor(HomeGesture.PINCH_IN))
        assertEquals(LauncherGestureAction.OPEN_SEARCH, gestures.actionFor(HomeGesture.ONE_FINGER_DOWN))
        assertEquals(LauncherGestureAction.OPEN_SETTINGS, gestures.actionFor(HomeGesture.TWO_FINGER_DOWN))
    }

    @Test
    fun gesturesNotStoredStillCountThroughTheirDefaults() {
        val sparse = HomeGestureSettings(actions = emptyMap())
        assertEquals(
            LauncherGestureAction.NONE,
            sparse.forAppLibraryHome().actionFor(HomeGesture.ONE_FINGER_LEFT),
        )
    }
}
