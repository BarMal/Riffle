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
import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneArrangement
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
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
    fun configuredLeadingDockOverridesTrailingTemplateVariant() {
        assertEquals(
            DockPosition.LEADING,
            resolveDockPosition(
                configuredDockPosition = DockPosition.LEADING,
                templateDockPosition = DockPosition.TRAILING,
            ),
        )
    }

    @Test
    fun configuredTrailingDockOverridesLeadingTemplateVariant() {
        assertEquals(
            DockPosition.TRAILING,
            resolveDockPosition(
                configuredDockPosition = DockPosition.TRAILING,
                templateDockPosition = DockPosition.LEADING,
            ),
        )
    }

    @Test
    fun unconfiguredDockDefersToTheTemplateVariant() {
        assertEquals(
            DockPosition.TRAILING,
            resolveDockPosition(
                configuredDockPosition = null,
                templateDockPosition = DockPosition.TRAILING,
            ),
        )
    }

    @Test
    fun unconfiguredDockWithNoTemplateFallsBackToLeading() {
        assertEquals(
            DockPosition.LEADING,
            resolveDockPosition(configuredDockPosition = null, templateDockPosition = null),
        )
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
    fun rotationLikeWindowResizeKeepsTheStageOnScreen() {
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

        // The wide layout's navigation lives on the dock, which is a sibling of this surface --
        // see DockDynamicSectionTest. What this surface owes a resize is that the stage survives it.
        composeRule.onNodeWithTag(ADAPTIVE_STAGE_STAGE_HEADER_TEST_TAG).assertIsDisplayed()
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

        // The supporting pane is what a confirmed flat posture buys at this width; a half-open or
        // tabletop device stays compact however large its reported bounds are.
        composeRule.onAllNodesWithText("Details").assertCountEquals(0)
        composeRule.runOnIdle { posture = AdaptiveStagePosture.UNFOLDED }
        composeRule.onNodeWithText("Details").assertIsDisplayed()
        composeRule.runOnIdle { posture = AdaptiveStagePosture.COMPACT }
        composeRule.onAllNodesWithText("Details").assertCountEquals(0)
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
