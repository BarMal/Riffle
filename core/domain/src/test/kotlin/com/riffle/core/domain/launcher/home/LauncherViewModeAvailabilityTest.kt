package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherViewModeAvailabilityTest {
    @Test
    fun defaultPolicyOnlyExposesStandardModeForEveryDeviceClass() {
        val policy = LauncherViewModeAvailability()

        HomeLayoutDeviceClass.entries.forEach { deviceClass ->
            assertEquals(listOf(LauncherViewMode.STANDARD_APP_DRAWER), policy.availableModes(deviceClass))
            assertTrue(policy.isAvailable(deviceClass, LauncherViewMode.STANDARD_APP_DRAWER))
            assertFalse(policy.isAvailable(deviceClass, LauncherViewMode.HOME_SCREEN_LIBRARY))
            assertFalse(policy.isAvailable(deviceClass, LauncherViewMode.CARD_INTERFACE))
        }
    }

    @Test
    fun explicitlyEnabledExperimentalModesAreAvailableForTheirDeviceClassOnly() {
        val policy =
            LauncherViewModeAvailability(
                enabledExperimentalModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.FOLDABLE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                        HomeLayoutDeviceClass.TABLET to setOf(LauncherViewMode.CARD_INTERFACE),
                    ),
            )

        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER),
            policy.availableModes(HomeLayoutDeviceClass.PHONE),
        )
        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.HOME_SCREEN_LIBRARY),
            policy.availableModes(HomeLayoutDeviceClass.FOLDABLE),
        )
        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.CARD_INTERFACE),
            policy.availableModes(HomeLayoutDeviceClass.TABLET),
        )
    }

    @Test
    fun deviceClassDecisionsRemainDeterministicWhenEnabledModeInputOrderChanges() {
        val policy =
            LauncherViewModeAvailability(
                enabledExperimentalModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to
                            linkedSetOf(
                                LauncherViewMode.CARD_INTERFACE,
                                LauncherViewMode.HOME_SCREEN_LIBRARY,
                            ),
                    ),
            )

        assertEquals(
            listOf(
                LauncherViewMode.STANDARD_APP_DRAWER,
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                LauncherViewMode.CARD_INTERFACE,
            ),
            policy.availableModes(HomeLayoutDeviceClass.PHONE),
        )
    }

    @Test
    fun unavailablePreferredModeFallsBackToStandardWithoutMutatingStoredLayouts() {
        val policy = LauncherViewModeAvailability()
        val cardKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.CARD_INTERFACE,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            )
        val cardLayout =
            HomeLayoutDefaults.standard(HomeLayoutDeviceClass.TABLET)
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet.standard()
                .withPreferredMode(
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                    mode = LauncherViewMode.CARD_INTERFACE,
                )
                .withLayout(cardKey, cardLayout)

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            ),
            policy.availableKeyFor(layoutSet, HomeLayoutDeviceClass.TABLET),
        )
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            layoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.TABLET],
        )
        assertEquals(LauncherViewMode.CARD_INTERFACE, layoutSet.layoutFor(cardKey).viewMode)
    }
}
