package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveStageAdaptiveLayoutInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun configuredLeadingRailOverridesTrailingTemplateVariant() {
        assertEquals(
            DockPosition.LEADING,
            resolveDockPosition(
                configuredDockPosition = DockPosition.LEADING,
                templateDockPosition = DockPosition.TRAILING,
            ),
        )
    }

    @Test
    fun configuredTrailingRailOverridesLeadingTemplateVariant() {
        assertEquals(
            DockPosition.TRAILING,
            resolveDockPosition(
                configuredDockPosition = DockPosition.TRAILING,
                templateDockPosition = DockPosition.LEADING,
            ),
        )
    }

    @Test
    fun unconfiguredRailDefersToTheTemplateVariant() {
        assertEquals(
            DockPosition.TRAILING,
            resolveDockPosition(
                configuredDockPosition = null,
                templateDockPosition = DockPosition.TRAILING,
            ),
        )
    }

    @Test
    fun unconfiguredRailWithNoTemplateFallsBackToLeading() {
        assertEquals(
            DockPosition.LEADING,
            resolveDockPosition(configuredDockPosition = null, templateDockPosition = null),
        )
    }

    @Test
    fun mediumWindowRendersStageRail() {
        setContent(widthDp = 800)

        // Previous/Next controls were removed in favor of tap/settle-drag navigation on the rail's
        // own card-stack visual (see AdaptiveStageStageHeader's customActions for the non-touch path) --
        // the rail's testTag is now the stable signal that this pane mode shows a rail at all.
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun largeWindowAddsSupportingDetailPane() {
        setContent(widthDp = 1_200)

        composeRule.onNodeWithText("Details").assertIsDisplayed()
    }

    @Test
    fun foldableTemplateRendersVisibleCanvasElementsAndPlacesStageSlot() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(1_200.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds()) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = 1_200,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val clock = composeRule.onNodeWithTag(adaptiveStageTemplateElementTestTag("clock")).fetchSemanticsNode().boundsInRoot
        val search = composeRule.onNodeWithTag(adaptiveStageTemplateElementTestTag("search")).fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(adaptiveStageTemplateElementTestTag("dock")).fetchSemanticsNode().boundsInRoot
        val stageSlot =
            composeRule.onNodeWithTag(adaptiveStageTemplateSlotTestTag("app-stage")).fetchSemanticsNode().boundsInRoot

        assertTrue(clock.top < search.top)
        assertTrue(search.bottom <= stageSlot.top)
        assertTrue(stageSlot.bottom <= dock.top)
    }

    @Test
    fun foldableTemplateRegionsDoNotCrossVerticalHinge() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(1_200.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds()) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = 1_200,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    separatingHinges =
                                        listOf(
                                            AdaptiveStageHingeBounds(
                                                leftDp = 584,
                                                topDp = 0,
                                                rightDp = 616,
                                                bottomDp = TEST_WINDOW_HEIGHT_DP,
                                            ),
                                        ),
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val hingeLeftPx = 584 * TEST_WINDOW_DENSITY
        val hingeRightPx = 616 * TEST_WINDOW_DENSITY
        val regionTags =
            listOf("clock", "search", "carousel", "dock").flatMap { id ->
                val baseTag = adaptiveStageTemplateElementTestTag(id)
                listOf(baseTag, adaptiveStageTemplatePaneFragmentTestTag(baseTag, 1))
            } +
                adaptiveStageTemplateSlotTestTag("app-stage").let { baseTag ->
                    listOf(baseTag, adaptiveStageTemplatePaneFragmentTestTag(baseTag, 1))
                }

        regionTags.forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag crosses the separating hinge: $bounds",
                bounds.right <= hingeLeftPx + PIXEL_TOLERANCE ||
                    bounds.left >= hingeRightPx - PIXEL_TOLERANCE,
            )
        }
    }

    @Test
    fun largeWindowKeepsSupportingDetailPaneInsideSafeInsets() {
        setContent(
            widthDp = INSET_TEST_WINDOW_WIDTH_DP,
            testDensity = INSET_TEST_DENSITY,
            windowInsets = WindowInsets(SAFE_START_PX, SAFE_TOP_PX, SAFE_END_PX, SAFE_BOTTOM_PX),
        )

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        val paneBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val windowBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(paneBounds.left >= windowBounds.left + SAFE_START_PX - PIXEL_TOLERANCE)
        assertTrue(paneBounds.top >= windowBounds.top + SAFE_TOP_PX - PIXEL_TOLERANCE)
        assertTrue(paneBounds.right <= windowBounds.right - SAFE_END_PX + PIXEL_TOLERANCE)
        assertTrue(paneBounds.bottom <= windowBounds.bottom - SAFE_BOTTOM_PX + PIXEL_TOLERANCE)
    }

    @Test
    fun topRailRendersAboveTheStageContentInsteadOfBesideIt() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.width(800.dp)
                                .height(TEST_WINDOW_HEIGHT_DP.dp)
                                .clipToBounds()
                                .testTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                    launcherSettings =
                                        LauncherSettings(
                                            cards = dockPositionSettings(DockPosition.TOP),
                                        ),
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = 800,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val railBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val windowBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG).fetchSemanticsNode().boundsInRoot

        // A TOP rail sits in a horizontal strip flush with the window's top edge and spanning its
        // full width, unlike the default LEADING rail, which is a narrow column offset to one side.
        assertTrue(railBounds.top <= windowBounds.top + PIXEL_TOLERANCE)
        assertTrue(railBounds.width >= windowBounds.width - PIXEL_TOLERANCE)
    }

    @Test
    fun minimumThreePaneVerticalHingeKeepsTrailingRailInsideSafeInsets() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(MINIMUM_HINGE_TEST_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.width(MINIMUM_HINGE_WINDOW_WIDTH_DP.dp)
                                .height(TEST_WINDOW_HEIGHT_DP.dp)
                                .clipToBounds()
                                .testTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                    launcherSettings =
                                        LauncherSettings(
                                            cards = dockPositionSettings(DockPosition.TRAILING),
                                        ),
                                ),
                            windowInsets =
                                WindowInsets(
                                    MINIMUM_HINGE_SAFE_INSET_PX,
                                    0,
                                    MINIMUM_HINGE_SAFE_INSET_PX,
                                    0,
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = MINIMUM_HINGE_WINDOW_WIDTH_DP,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    separatingHinges =
                                        listOf(
                                            AdaptiveStageHingeBounds(
                                                leftDp = 376,
                                                topDp = 0,
                                                rightDp = 408,
                                                bottomDp = TEST_WINDOW_HEIGHT_DP,
                                            ),
                                        ),
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val railBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val paneBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val windowBounds = composeRule.onNodeWithTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(paneBounds.right <= railBounds.left + PIXEL_TOLERANCE)
        assertTrue(railBounds.right <= windowBounds.right - MINIMUM_HINGE_SAFE_INSET_PX + PIXEL_TOLERANCE)
    }

    @Test
    fun rotationLikeWindowResizeKeepsAdaptiveStageNavigationReachable() {
        var widthDp by mutableIntStateOf(360)
        var heightDp by mutableIntStateOf(800)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.width(widthDp.dp)
                                .height(heightDp.dp)
                                .clipToBounds(),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowLayout =
                                AdaptiveStageWindowLayout(widthDp, heightDp, posture = AdaptiveStagePosture.UNFOLDED),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle {
            widthDp = 800
            heightDp = 360
        }

        composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun compactUnfoldedCompactKeepsStageManagerPostureGated() {
        var posture by mutableStateOf(AdaptiveStagePosture.COMPACT)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier = Modifier.width(1_200.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds(),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowLayout = AdaptiveStageWindowLayout(1_200, TEST_WINDOW_HEIGHT_DP, posture = posture),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onAllNodesWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).assertCountEquals(0)
        composeRule.runOnIdle { posture = AdaptiveStagePosture.UNFOLDED }
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).assertIsDisplayed()
        composeRule.runOnIdle { posture = AdaptiveStagePosture.COMPACT }
        composeRule.onAllNodesWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun splitArrangementRendersSupportingPaneAndStagePagerTogether() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(360.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds()) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                    launcherSettings =
                                        LauncherSettings(
                                            cards =
                                                CardsSettings(
                                                    adaptiveStagePaneArrangement = AdaptiveStagePaneArrangement.SPLIT,
                                                ),
                                        ),
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = 360,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        // Both the upper detail region and the lower stage pager/spine region must be present
        // simultaneously -- proving this is a genuine split, not one replacing the other.
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_SUPPORTING_PANE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Install an app to create your first stage.").assertIsDisplayed()
    }

    @Test
    fun everyStageIsReachableInTheRailByScrollingRatherThanSteppingThroughAFan() {
        // More stages than fit the strip at once. The rail scrolls, so the last one is reachable
        // directly instead of one settle-drag per card between here and there.
        val apps = railTestApps(count = 16)
        val actions = mutableListOf<LauncherShellAction>()
        setRailContent(apps = apps, onAction = actions::add)

        val last = apps.last()
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_RAIL_TEST_TAG)
            .performScrollToNode(hasContentDescription("${last.label}. Open stage"))
        composeRule.onNodeWithContentDescription("${last.label}. Open stage").performClick()

        composeRule.runOnIdle {
            assertEquals(
                LauncherShellAction.SelectAppStage(
                    AppStageId(last.identity.packageName, last.identity.profile.id),
                ),
                actions.last(),
            )
        }
    }

    @Test
    fun theRailOpensScrolledToTheSelectedStageRatherThanAtItsStart() {
        // Selection also moves from outside the rail, so the tile it names has to be the one on
        // screen -- otherwise a restored selection leaves the rail showing an unrelated stretch.
        val apps = railTestApps(count = 16)
        val selected = apps.last()
        setRailContent(
            apps = apps,
            selectedStageId = AppStageId(selected.identity.packageName, selected.identity.profile.id),
            onAction = {},
        )

        composeRule.onNodeWithContentDescription("${selected.label}, selected. Open stage").assertIsDisplayed()
    }

    /**
     * The dock edge is stored per layout, so a test that wants one has to name the layout the
     * surface will actually look up -- the shell's own active key.
     */
    private fun activeLayoutKey(): HomeLayoutKey = LauncherShellState().homeLayoutSet.activeKey

    private fun dockPositionSettings(position: DockPosition): CardsSettings =
        CardsSettings(dockPositionByLayout = mapOf(activeLayoutKey() to position))

    private fun railTestApps(count: Int): List<InstalledApp> =
        (1..count).map { index ->
            val packageName = "com.example.stage$index"
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName(packageName),
                        activityName = AppActivityName("$packageName.Main"),
                        profile = AppProfile.personal(),
                    ),
                label = "Stage %02d".format(index),
            )
        }

    private fun setRailContent(
        apps: List<InstalledApp>,
        selectedStageId: AppStageId? = null,
        onAction: (LauncherShellAction) -> Unit,
    ) {
        val stageIds = apps.map { app -> AppStageId(app.identity.packageName, app.identity.profile.id) }
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier = Modifier.width(1_200.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds(),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state =
                                LauncherShellState(
                                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                                    installedApps = apps,
                                    launcherSettings =
                                        LauncherSettings(
                                            cards =
                                                CardsSettings(
                                                    dockPositionByLayout =
                                                        mapOf(activeLayoutKey() to DockPosition.LEADING),
                                                    stagePreferencesByLayout =
                                                        mapOf(
                                                            HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER) to
                                                                AppStagePreferences(
                                                                    pinnedStageIds = stageIds,
                                                                    selectedStageId = selectedStageId,
                                                                ),
                                                        ),
                                                ),
                                        ),
                                ),
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = 1_200,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }

    private fun setContent(
        widthDp: Int,
        testDensity: Float = TEST_WINDOW_DENSITY,
        windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        composeRule.setContent {
            // Make the physical test host represent the requested adaptive dp window.
            CompositionLocalProvider(LocalDensity provides Density(testDensity)) {
                MaterialTheme {
                    Box(
                        modifier =
                            Modifier.width(widthDp.dp)
                                .height(TEST_WINDOW_HEIGHT_DP.dp)
                                .clipToBounds()
                                .testTag(ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG),
                    ) {
                        AdaptiveStageAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowInsets = windowInsets,
                            windowLayout =
                                AdaptiveStageWindowLayout(
                                    widthDp = widthDp,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = AdaptiveStagePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val TEST_WINDOW_DENSITY = 0.3f
        const val TEST_WINDOW_HEIGHT_DP = 800
        const val INSET_TEST_WINDOW_WIDTH_DP = 1_400
        const val INSET_TEST_DENSITY = 0.25f
        const val MINIMUM_HINGE_TEST_DENSITY = 0.25f
        const val MINIMUM_HINGE_WINDOW_WIDTH_DP = 880
        const val MINIMUM_HINGE_SAFE_INSET_PX = 2
        const val SAFE_START_PX = 24
        const val SAFE_TOP_PX = 16
        const val SAFE_END_PX = 48
        const val SAFE_BOTTOM_PX = 32
        const val PIXEL_TOLERANCE = 1f
        const val ADAPTIVE_STAGE_ADAPTIVE_TEST_WINDOW_TAG = "adaptive-stage-adaptive-test-window"
    }
}
