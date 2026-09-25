package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LibraryReturnTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryReturnTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    @Test
    fun returningHomeIsTheDefault() {
        assertEquals(LibraryReturnTarget.HOME, AppDrawerSettings().afterLeavingLibrary)
    }

    @Test
    fun everyTriggerReturnsHomeFromLibraryWhenTheSettingIsHome() {
        LibraryExitTrigger.entries.forEach { trigger ->
            assertTrue(
                trigger.returnsHome(current = ModeSurface.LIBRARY, returnTarget = LibraryReturnTarget.HOME),
                "$trigger should return Home",
            )
        }
    }

    @Test
    fun noTriggerLeavesLibraryWhenTheSettingIsLibrary() {
        LibraryExitTrigger.entries.forEach { trigger ->
            assertFalse(
                trigger.returnsHome(current = ModeSurface.LIBRARY, returnTarget = LibraryReturnTarget.LIBRARY),
                "$trigger should stay on Library",
            )
        }
    }

    @Test
    fun noTriggerMovesHomeWhateverTheSetting() {
        LibraryExitTrigger.entries.forEach { trigger ->
            LibraryReturnTarget.entries.forEach { target ->
                assertFalse(
                    trigger.returnsHome(current = ModeSurface.HOME, returnTarget = target),
                    "$trigger under $target should leave Home alone",
                )
            }
        }
    }

    @Test
    fun appLaunchFromLibraryReturnsToHomeByDefaultAndStaysOnLibraryWhenAsked() {
        assertModeAfter(LibraryExitTrigger.APP_LAUNCH)
    }

    @Test
    fun homePressFromLibraryReturnsToHomeByDefaultAndStaysOnLibraryWhenAsked() {
        assertModeAfter(LibraryExitTrigger.HOME_PRESS)
    }

    @Test
    fun backFromLibraryReturnsToHomeByDefaultAndStaysOnLibraryWhenAsked() {
        assertModeAfter(LibraryExitTrigger.BACK)
    }

    @Test
    fun coldStartOnLibraryOpensHomeByDefaultAndLibraryOnlyWhenAsked() {
        assertModeAfter(LibraryExitTrigger.COLD_START)
    }

    @Test
    fun triggersOnHomeKeepTheModeUnderEitherSetting() {
        val onCards = layoutSet(active = LauncherViewMode.CARD_INTERFACE)
        LibraryExitTrigger.entries.forEach { trigger ->
            LibraryReturnTarget.entries.forEach { target ->
                assertNull(onCards.modeAfterLeavingLibrary(trigger, target), "$trigger under $target")
            }
        }
    }

    @Test
    fun homeIsTheFirstNonLibraryModeTheDeviceClassMovesBetween() {
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            homeModeAmong(listOf(LauncherViewMode.HOME_SCREEN_LIBRARY, LauncherViewMode.CARD_INTERFACE)),
        )
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            homeModeAmong(
                listOf(
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.CARD_INTERFACE,
                ),
            ),
        )
        assertEquals(DEFAULT_HOME_MODE, homeModeAmong(listOf(LauncherViewMode.HOME_SCREEN_LIBRARY)))
    }

    @Test
    fun leavingLibraryGoesToTheDeviceClassesOwnHomeMode() {
        val standardHome =
            layoutSet(active = LauncherViewMode.HOME_SCREEN_LIBRARY)
                .withModeRing(
                    deviceClass = phone,
                    ring = ModeRing(listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.HOME_SCREEN_LIBRARY)),
                )

        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            standardHome.modeAfterLeavingLibrary(LibraryExitTrigger.BACK, LibraryReturnTarget.HOME),
        )
    }

    private fun assertModeAfter(trigger: LibraryExitTrigger) {
        val onLibrary = layoutSet(active = LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            onLibrary.modeAfterLeavingLibrary(trigger, LibraryReturnTarget.HOME),
            "$trigger with the default should land on Home",
        )
        assertNull(
            onLibrary.modeAfterLeavingLibrary(trigger, LibraryReturnTarget.LIBRARY),
            "$trigger with Library chosen should stay on Library",
        )
    }

    private fun layoutSet(active: LauncherViewMode): HomeLayoutSet =
        HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard(phone).copy(viewMode = active))
}
