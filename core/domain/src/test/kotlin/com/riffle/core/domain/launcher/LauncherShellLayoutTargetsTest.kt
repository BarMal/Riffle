package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherShellLayoutTargetsTest {
    private val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
    private val layoutSet =
        HomeLayoutSet(
            activeKey = phoneKey,
            layouts = mapOf(phoneKey to HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE)),
        )

    @Test
    fun modeSwitchOffSettingsTargetsTheDeviceBeingHeldEvenWhenSettingsPointsElsewhere() {
        val state =
            LauncherShellState(
                destination = ShellDestination.HOME,
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )

        assertEquals(HomeLayoutDeviceClass.PHONE, state.modeSwitchTargetDeviceClass)
    }

    @Test
    fun modeSwitchInSettingsTargetsTheDeviceSettingsIsConfiguring() {
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )

        assertEquals(HomeLayoutDeviceClass.FOLDABLE, state.modeSwitchTargetDeviceClass)
    }

    @Test
    fun modeChosenForTheActiveDeviceSwitchesWhatIsOnScreen() {
        val updated =
            layoutSet.withModeChosenFor(
                deviceClass = HomeLayoutDeviceClass.PHONE,
                mode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            )

        assertEquals(
            HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE),
            updated.activeKey,
        )
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            updated.preferredModesByDeviceClass[HomeLayoutDeviceClass.PHONE],
        )
    }

    @Test
    fun modeChosenForAnotherDeviceOnlyRecordsItsPreference() {
        val updated =
            layoutSet.withModeChosenFor(
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                mode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            )

        assertEquals(phoneKey, updated.activeKey)
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            updated.preferredModesByDeviceClass[HomeLayoutDeviceClass.FOLDABLE],
        )
    }
}
