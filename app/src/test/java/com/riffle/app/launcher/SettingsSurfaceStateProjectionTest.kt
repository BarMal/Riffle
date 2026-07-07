package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsSurfaceStateProjectionTest {
    @Test
    fun dockPageProjectsSelectedSettingsDeviceClassDockInsteadOfActiveDock() {
        val phoneKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val foldableKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.FOLDABLE)
        val phoneDock = DockModel(capacity = 4, iconSizeDp = 36, itemSpacingDp = 4)
        val foldableDock = DockModel(capacity = 8, iconSizeDp = 56, itemSpacingDp = 18)
        val phoneLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE).copy(dock = phoneDock)
        val foldableLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE).copy(dock = foldableDock)
        val layoutSet =
            HomeLayoutSet(
                activeKey = phoneKey,
                layouts = mapOf(phoneKey to phoneLayout, foldableKey to foldableLayout),
            )
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = phoneLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableLayoutDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            )

        val surfaceState = state.settingsSurfaceState()

        assertEquals(HomeLayoutDeviceClass.FOLDABLE, surfaceState.selectedLayoutDeviceClass)
        assertEquals(foldableDock, surfaceState.homeLayout.dock)
        assertNotEquals(state.homeLayout.dock, surfaceState.homeLayout.dock)
    }

    @Test
    fun dockPageProjectionKeepsDecodedDeviceClassDockDefaultsSeparate() {
        val layoutSet =
            decodeHomeLayoutSet(
                """
                {
                  "type": "homeLayoutSet",
                  "active": {
                    "viewMode": "STANDARD_APP_DRAWER",
                    "deviceClass": "PHONE"
                  },
                  "layouts": [
                    {
                      "key": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "deviceClass": "PHONE"
                      },
                      "layout": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "selectedPageId": "home",
                        "pages": [
                          {
                            "id": "home",
                            "columns": 4,
                            "rows": 5,
                            "items": []
                          }
                        ],
                        "dock": {
                          "items": []
                        }
                      }
                    },
                    {
                      "key": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "deviceClass": "FOLDABLE"
                      },
                      "layout": {
                        "viewMode": "STANDARD_APP_DRAWER",
                        "selectedPageId": "home",
                        "pages": [
                          {
                            "id": "home",
                            "columns": 5,
                            "rows": 6,
                            "items": []
                          }
                        ],
                        "dock": {
                          "items": []
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableLayoutDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            )

        val surfaceDock = state.settingsSurfaceState().homeLayout.dock

        assertEquals(HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE).dock, surfaceDock)
        assertNotEquals(HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE).dock, surfaceDock)
    }

    @Test
    fun projectionHidesUnavailableViewModesAndUsesAvailableSettingsLayout() {
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        val cardKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.PHONE)
        val cardLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE)
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet(
                activeKey = standardKey,
                layouts =
                    mapOf(
                        standardKey to HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE),
                        cardKey to cardLayout,
                    ),
                preferredModesByDeviceClass =
                    mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE),
            )
        val state =
            LauncherShellState(
                destination = ShellDestination.SETTINGS,
                homeLayout = layoutSet.activeLayout,
                homeLayoutSet = layoutSet,
                settingsLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
            )

        val surfaceState =
            state.settingsSurfaceState(
                viewModeAvailability =
                    LauncherViewModeAvailability(
                        enabledExperimentalModesByDeviceClass =
                            mapOf(HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)),
                    ),
            )

        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.HOME_SCREEN_LIBRARY),
            surfaceState.availableLauncherViewModes,
        )
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, surfaceState.homeLayout.viewMode)
    }
}
