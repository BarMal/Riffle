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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.TimeScapeHingeBounds
import com.riffle.core.domain.launcher.cards.TimeScapePosture
import com.riffle.core.domain.launcher.cards.TimeScapeRailSide
import com.riffle.core.domain.launcher.cards.TimeScapeWindowLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeScapeAdaptiveLayoutInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mediumWindowUsesNamedStageRailControls() {
        setContent(widthDp = 800)

        composeRule.onNodeWithText("Stages").assertIsDisplayed()
        composeRule.onNodeWithText("Previous").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
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
                        TimeScapeAppStageSurface(
                            state =
                                LauncherShellState(
                                    settingsLayoutDeviceClass = HomeLayoutDeviceClass.FOLDABLE,
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                ),
                            windowLayout =
                                TimeScapeWindowLayout(
                                    widthDp = 1_200,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = TimeScapePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val clock = composeRule.onNodeWithTag(timeScapeTemplateElementTestTag("clock")).fetchSemanticsNode().boundsInRoot
        val search = composeRule.onNodeWithTag(timeScapeTemplateElementTestTag("search")).fetchSemanticsNode().boundsInRoot
        val dock = composeRule.onNodeWithTag(timeScapeTemplateElementTestTag("dock")).fetchSemanticsNode().boundsInRoot
        val stageSlot =
            composeRule.onNodeWithTag(timeScapeTemplateSlotTestTag("app-stage")).fetchSemanticsNode().boundsInRoot

        assertTrue(clock.top < search.top)
        assertTrue(search.bottom <= stageSlot.top)
        assertTrue(stageSlot.bottom <= dock.top)
    }

    @Test
    fun largeWindowKeepsSupportingDetailPaneInsideSafeInsets() {
        setContent(
            widthDp = INSET_TEST_WINDOW_WIDTH_DP,
            testDensity = INSET_TEST_DENSITY,
            windowInsets = WindowInsets(SAFE_START_PX, SAFE_TOP_PX, SAFE_END_PX, SAFE_BOTTOM_PX),
        )

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        val paneBounds = composeRule.onNodeWithTag(TIME_SCAPE_SUPPORTING_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val windowBounds = composeRule.onNodeWithTag(TIME_SCAPE_ADAPTIVE_TEST_WINDOW_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(paneBounds.left >= windowBounds.left + SAFE_START_PX - PIXEL_TOLERANCE)
        assertTrue(paneBounds.top >= windowBounds.top + SAFE_TOP_PX - PIXEL_TOLERANCE)
        assertTrue(paneBounds.right <= windowBounds.right - SAFE_END_PX + PIXEL_TOLERANCE)
        assertTrue(paneBounds.bottom <= windowBounds.bottom - SAFE_BOTTOM_PX + PIXEL_TOLERANCE)
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
                                .testTag(TIME_SCAPE_ADAPTIVE_TEST_WINDOW_TAG),
                    ) {
                        TimeScapeAppStageSurface(
                            state =
                                LauncherShellState(
                                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                                    launcherSettings =
                                        LauncherSettings(
                                            cards = CardsSettings(timeScapeRailSide = TimeScapeRailSide.TRAILING),
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
                                TimeScapeWindowLayout(
                                    widthDp = MINIMUM_HINGE_WINDOW_WIDTH_DP,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    separatingHinges =
                                        listOf(
                                            TimeScapeHingeBounds(
                                                leftDp = 376,
                                                topDp = 0,
                                                rightDp = 408,
                                                bottomDp = TEST_WINDOW_HEIGHT_DP,
                                            ),
                                        ),
                                    posture = TimeScapePosture.UNFOLDED,
                                ),
                            onAction = {},
                        )
                    }
                }
            }
        }

        val railBounds = composeRule.onNodeWithText("Stages").fetchSemanticsNode().boundsInRoot
        val paneBounds = composeRule.onNodeWithTag(TIME_SCAPE_SUPPORTING_PANE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val windowBounds = composeRule.onNodeWithTag(TIME_SCAPE_ADAPTIVE_TEST_WINDOW_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(paneBounds.right <= railBounds.left + PIXEL_TOLERANCE)
        assertTrue(railBounds.right <= windowBounds.right - MINIMUM_HINGE_SAFE_INSET_PX + PIXEL_TOLERANCE)
    }

    @Test
    fun rotationLikeWindowResizeKeepsTimeScapeNavigationReachable() {
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
                        TimeScapeAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowLayout =
                                TimeScapeWindowLayout(widthDp, heightDp, posture = TimeScapePosture.UNFOLDED),
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

        composeRule.onNodeWithText("Stages").assertIsDisplayed()
        composeRule.onNodeWithText("Next").assertIsDisplayed()
    }

    @Test
    fun compactUnfoldedCompactKeepsStageManagerPostureGated() {
        var posture by mutableStateOf(TimeScapePosture.COMPACT)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(TEST_WINDOW_DENSITY)) {
                MaterialTheme {
                    Box(
                        modifier = Modifier.width(1_200.dp).height(TEST_WINDOW_HEIGHT_DP.dp).clipToBounds(),
                    ) {
                        TimeScapeAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowLayout = TimeScapeWindowLayout(1_200, TEST_WINDOW_HEIGHT_DP, posture = posture),
                            onAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onAllNodesWithText("Stages").assertCountEquals(0)
        composeRule.runOnIdle { posture = TimeScapePosture.UNFOLDED }
        composeRule.onNodeWithText("Stages").assertIsDisplayed()
        composeRule.runOnIdle { posture = TimeScapePosture.COMPACT }
        composeRule.onAllNodesWithText("Stages").assertCountEquals(0)
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
                                .testTag(TIME_SCAPE_ADAPTIVE_TEST_WINDOW_TAG),
                    ) {
                        TimeScapeAppStageSurface(
                            state = LauncherShellState(notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
                            windowInsets = windowInsets,
                            windowLayout =
                                TimeScapeWindowLayout(
                                    widthDp = widthDp,
                                    heightDp = TEST_WINDOW_HEIGHT_DP,
                                    posture = TimeScapePosture.UNFOLDED,
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
        const val TIME_SCAPE_ADAPTIVE_TEST_WINDOW_TAG = "timescape-adaptive-test-window"
    }
}
