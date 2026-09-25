package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutSetTest {
    @Test
    fun standardSetStartsOnStandardPhoneLayout() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER), layoutSet.activeKey)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.activeLayout.viewMode)
        assertTrue(layoutSet.activeLayout.selectedPage.items.isEmpty())
    }

    @Test
    fun selectingModeUsesSeparateLayoutForThatMode() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY), layoutSet.activeKey)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.activeLayout.viewMode)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.layoutFor(standardKey).viewMode)
    }

    @Test
    fun selectingUnavailableModeFallsBackToStandardWithoutMutatingExperimentalLayout() {
        val cardKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)
        val cardLayout =
            HomeLayoutDefaults.standard()
                .copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val layoutSet =
            HomeLayoutSet.standard()
                .withLayout(cardKey, cardLayout)
                .selectMode(
                    mode = LauncherViewMode.CARD_INTERFACE,
                    availability = LauncherViewModeAvailability(),
                )

        assertEquals(standardKey, layoutSet.activeKey)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.activeLayout.viewMode)
        assertEquals(cardLayout, layoutSet.layoutFor(cardKey))
    }

    @Test
    fun selectingEnabledExperimentalModeUsesThatMode() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(
                    mode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                    availability =
                        LauncherViewModeAvailability(
                            enabledExperimentalModesByDeviceClass =
                                mapOf(HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)),
                        ),
                )

        assertEquals(HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY), layoutSet.activeKey)
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            layoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.PHONE],
        )
    }

    @Test
    fun selectingNewModesForFoldablePreservesDockedApps() {
        val foldableKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )
        val dockedApps = listOf(appShortcut("phone"), appShortcut("camera"))
        val layoutSet =
            HomeLayoutSet(
                activeKey = foldableKey,
                layouts =
                    mapOf(
                        foldableKey to
                            HomeLayoutDefaults
                                .standard(HomeLayoutDeviceClass.FOLDABLE)
                                .copy(
                                    dock =
                                        HomeLayoutDefaults
                                            .standard(HomeLayoutDeviceClass.FOLDABLE)
                                            .dock
                                            .copy(items = dockedApps),
                                ),
                    ),
            )

        val cards = layoutSet.selectMode(LauncherViewMode.CARD_INTERFACE)
        val library = cards.selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(dockedApps, cards.activeLayout.dock.items)
        assertEquals(dockedApps, library.activeLayout.dock.items)
    }

    @Test
    fun selectingDeviceClassUsesSeparateLayoutForThatClass() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE)

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            ),
            layoutSet.activeKey,
        )
        assertEquals(HomeLayoutDeviceClass.PHONE, standardKey.deviceClass)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.activeLayout.viewMode)
        assertEquals(GridDimensions(columns = 5, rows = 6), layoutSet.activeLayout.settings.grid.dimensions)
        assertEquals(6, layoutSet.activeLayout.dock.capacity)
    }

    @Test
    fun selectingDeviceClassUsesPreferredModeForThatClass() {
        val layoutSet =
            HomeLayoutSet.standard()
                .withPreferredMode(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    mode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                )
                .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE)

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            ),
            layoutSet.activeKey,
        )
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.activeLayout.viewMode)
    }

    @Test
    fun selectingDeviceClassWithUnavailablePreferredModeFallsBackWithoutMutatingExperimentalLayout() {
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
                .selectDeviceClass(
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                    availability = LauncherViewModeAvailability(),
                )

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            ),
            layoutSet.activeKey,
        )
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            layoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.TABLET],
        )
        assertEquals(cardLayout, layoutSet.layoutFor(cardKey))
    }

    @Test
    fun selectingDeviceClassWithEnabledPreferredModeUsesThatMode() {
        val layoutSet =
            HomeLayoutSet.standard()
                .withPreferredMode(
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                    mode = LauncherViewMode.CARD_INTERFACE,
                )
                .selectDeviceClass(
                    deviceClass = HomeLayoutDeviceClass.TABLET,
                    availability =
                        LauncherViewModeAvailability(
                            enabledExperimentalModesByDeviceClass =
                                mapOf(HomeLayoutDeviceClass.TABLET to setOf(LauncherViewMode.CARD_INTERFACE)),
                        ),
                )

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.CARD_INTERFACE,
                deviceClass = HomeLayoutDeviceClass.TABLET,
            ),
            layoutSet.activeKey,
        )
    }

    @Test
    fun updatingActiveLayoutDoesNotMutateOtherModeLayouts() {
        val libraryPage =
            HomeLayoutDefaults.standard()
                .selectedPage
                .copy(id = LauncherPageId("library-home"))
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                .withActiveLayout(
                    HomeLayoutDefaults.standard().copy(
                        viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                        pages = listOf(libraryPage),
                        selectedPageId = libraryPage.id,
                    ),
                )

        assertEquals(LauncherPageId("home"), layoutSet.layoutFor(standardKey).selectedPageId)
        assertEquals(LauncherPageId("library-home"), layoutSet.activeLayout.selectedPageId)
    }

    @Test
    fun updatingSpecificLayoutDoesNotChangeActiveKey() {
        val foldableKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )
        val foldablePage =
            HomeLayoutDefaults.standard()
                .selectedPage
                .copy(id = LauncherPageId("foldable-home"))
        val layoutSet =
            HomeLayoutSet.standard()
                .withLayout(
                    key = foldableKey,
                    layout =
                        HomeLayoutDefaults.standard().copy(
                            pages = listOf(foldablePage),
                            selectedPageId = foldablePage.id,
                        ),
                )

        assertEquals(standardKey, layoutSet.activeKey)
        assertEquals(LauncherPageId("home"), layoutSet.activeLayout.selectedPageId)
        assertEquals(LauncherPageId("foldable-home"), layoutSet.layoutFor(foldableKey).selectedPageId)
    }

    @Test
    fun leavingCardsIsThePreviousModeInTheDefaultRing() {
        val layoutSet =
            libraryPhone()
                .withModeRing(HomeLayoutDeviceClass.PHONE, ModeRing.DEFAULT)
                .selectMode(LauncherViewMode.CARD_INTERFACE)

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.previousMode())
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.nextMode())
        assertEquals(1, layoutSet.activeModeIndex)
    }

    @Test
    fun nextAndPreviousWalkTheActiveDeviceClassRingInOrder() {
        val ring =
            ModeRing(
                listOf(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.CARD_INTERFACE,
                ),
            )
        val layoutSet = HomeLayoutSet.standard().withModeRing(HomeLayoutDeviceClass.PHONE, ring)

        assertEquals(0, layoutSet.activeModeIndex)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.nextMode())
        assertEquals(LauncherViewMode.CARD_INTERFACE, layoutSet.previousMode())
    }

    @Test
    fun aFreshSetOnStandardHasARingHoldingStandard() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(
            listOf(
                LauncherViewMode.STANDARD_APP_DRAWER,
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                LauncherViewMode.CARD_INTERFACE,
            ),
            layoutSet.activeModeRing.modes,
        )
        assertEquals(ModeRing.DEFAULT, libraryPhone().activeModeRing)
    }

    @Test
    fun selectingAModeOutsideTheRingAddsItNextToTheModeItWasChosenFrom() {
        val layoutSet =
            libraryPhone()
                .withModeRing(HomeLayoutDeviceClass.PHONE, ModeRing.DEFAULT)
                .selectMode(LauncherViewMode.STANDARD_APP_DRAWER)

        assertEquals(
            listOf(
                LauncherViewMode.HOME_SCREEN_LIBRARY,
                LauncherViewMode.STANDARD_APP_DRAWER,
                LauncherViewMode.CARD_INTERFACE,
            ),
            layoutSet.activeModeRing.modes,
        )
        assertEquals(1, layoutSet.activeModeIndex)
    }

    @Test
    fun theActiveModeIsAlwaysInItsRingWhateverSelectsIt() {
        val ring = ModeRing(listOf(LauncherViewMode.HOME_SCREEN_LIBRARY, LauncherViewMode.CARD_INTERFACE))
        val start =
            libraryPhone()
                .withModeRing(HomeLayoutDeviceClass.PHONE, ring)
                .withModeRing(HomeLayoutDeviceClass.FOLDABLE, ring)
        val results =
            listOf(
                start.selectMode(LauncherViewMode.STANDARD_APP_DRAWER),
                start.withModeChosenFor(HomeLayoutDeviceClass.FOLDABLE, LauncherViewMode.STANDARD_APP_DRAWER)
                    .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
                start.selectDeviceClass(HomeLayoutDeviceClass.TABLET),
            )

        results.forEach { layoutSet ->
            assertTrue(layoutSet.activeKey.viewMode in layoutSet.activeModeRing, layoutSet.toString())
            layoutSet.preferredModesByDeviceClass.forEach { (deviceClass, mode) ->
                assertTrue(mode in layoutSet.modeRingFor(deviceClass), "$deviceClass $mode")
            }
        }
    }

    @Test
    fun removingTheActiveModeFromTheRingMovesToItsNeighbour() {
        val threeModes =
            ModeRing(
                listOf(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.CARD_INTERFACE,
                ),
            )
        val layoutSet =
            libraryPhone()
                .withModeRing(HomeLayoutDeviceClass.PHONE, threeModes)
                .withModeRing(
                    HomeLayoutDeviceClass.PHONE,
                    threeModes.withModeEnabled(LauncherViewMode.HOME_SCREEN_LIBRARY, enabled = false),
                )

        // Library was in the middle; Cards slides into its place.
        assertEquals(LauncherViewMode.CARD_INTERFACE, layoutSet.activeKey.viewMode)
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            layoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.PHONE],
        )
        assertEquals(
            listOf(LauncherViewMode.STANDARD_APP_DRAWER, LauncherViewMode.CARD_INTERFACE),
            layoutSet.activeModeRing.modes,
        )
    }

    @Test
    fun removingAnotherDeviceClassModeMovesOnlyItsPreference() {
        val threeModes =
            ModeRing(
                listOf(
                    LauncherViewMode.STANDARD_APP_DRAWER,
                    LauncherViewMode.HOME_SCREEN_LIBRARY,
                    LauncherViewMode.CARD_INTERFACE,
                ),
            )
        val layoutSet =
            libraryPhone()
                .withModeChosenFor(HomeLayoutDeviceClass.FOLDABLE, LauncherViewMode.CARD_INTERFACE)
                .withModeRing(HomeLayoutDeviceClass.FOLDABLE, threeModes)
                .withModeRing(
                    HomeLayoutDeviceClass.FOLDABLE,
                    threeModes.withModeEnabled(LauncherViewMode.CARD_INTERFACE, enabled = false),
                )

        // Cards was last, so the mode before it takes over.
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            layoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.FOLDABLE],
        )
        assertEquals(HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY), layoutSet.activeKey)
    }

    @Test
    fun reorderingTheRingKeepsTheActiveMode() {
        val layoutSet =
            libraryPhone()
                .withModeRing(
                    HomeLayoutDeviceClass.PHONE,
                    ModeRing.DEFAULT.withModeMoved(LauncherViewMode.CARD_INTERFACE, -1),
                )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.activeKey.viewMode)
        assertEquals(1, layoutSet.activeModeIndex)
    }

    private fun libraryPhone(): HomeLayoutSet =
        HomeLayoutSet.standard().selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY).let { set ->
            // selectMode from a fresh Standard set adds Library to Standard's fallback ring; start
            // these tests from the default ring instead.
            set.copy(
                modeRingsByDeviceClass = emptyMap(),
                preferredModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to LauncherViewMode.HOME_SCREEN_LIBRARY,
                    ),
            )
        }

    private val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)

    private fun appShortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )
}
