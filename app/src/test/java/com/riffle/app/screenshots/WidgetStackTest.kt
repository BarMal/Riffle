package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.HOME_WIDGET_STACK_DOTS_TEST_TAG
import com.riffle.app.launcher.HOME_WIDGET_STACK_TEST_TAG
import com.riffle.app.launcher.HomeWidgetStack
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.ShortcutContextSurface
import com.riffle.app.launcher.WidgetStackPresentation
import com.riffle.app.launcher.homeWidgetStackProbeKey
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.restacked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A hosted-widget stack with [labels] (made-up names, no provider behind them) shown at [shown]. */
internal fun fixtureStack(
    labels: List<String> = listOf("Weather", "Calendar", "Notes"),
    shown: Int = 1,
    span: GridSpan = GridSpan(2, 2),
): WidgetItem =
    WidgetItem(
        id = LauncherItemId("stack-1"),
        appWidgetId = HostedWidgetId(1),
        label = labels.first(),
        placement = GridPlacement(GridCell(0, 0), span),
    ).restacked(labels.mapIndexed { index, label -> StackedWidget(HostedWidgetId(index + 1), label) }, shown)

/** A stack of Riffle's own widgets. */
internal fun fixtureGlanceStack(shown: Int = 0): WidgetItem =
    WidgetItem(
        id = LauncherItemId("stack-1"),
        appWidgetId = HostedWidgetId(0),
        label = "Now playing",
        placement = GridPlacement(GridCell(0, 0), GridSpan(2, 2)),
    ).restacked(
        GlanceWidgetKind.entries.map { kind ->
            StackedWidget(HostedWidgetId(0), kind.defaultLabel, kind.resizeConstraints, kind)
        },
        shown,
    )

/**
 * A widget stack drawn by the real [HomeWidgetStack] (ADR 0003, Amendment 14): at rest with its dots, mid swipe,
 * the swipe arbitration (the strip always; anywhere for Riffle's own widgets, not over a platform widget), the
 * accessibility node, the 48 dp strip, reduced motion, which widgets hold a view, and frame stability.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WidgetStackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val viewsCreatedFor = mutableListOf<Int>()
    private val probe = RecompositionProbe()
    private var widget by mutableStateOf(fixtureStack())
    private var presentation by mutableStateOf(WidgetStackPresentation())

    private val factory =
        // No host view: the frame draws the widget's name, which is what the goldens show.
        HomeWidgetViewFactory { _, member ->
            viewsCreatedFor += member.appWidgetId.value
            null
        }

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    Box(modifier = Modifier.padding(24.dp).size(STACK_SIZE_DP.dp)) {
                        HomeWidgetStack(
                            widget = widget,
                            isEditing = false,
                            onAction = { action ->
                                actions += action
                                if (action is LauncherShellAction.ShowStackedWidget) {
                                    widget = widget.copy(shownIndex = action.index)
                                }
                            },
                            contextSurface = ShortcutContextSurface.HOME,
                            widgetViewFactory = factory,
                            presentation = presentation,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun shownEvents() = actions.filterIsInstance<LauncherShellAction.ShowStackedWidget>()

    @Test
    fun aStackAtRestShowsTheShownWidgetAndItsDots() {
        show()
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).captureRoboImage()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun aStackAtRestInTheDarkTheme() {
        show()
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).captureRoboImage()
    }

    @Test
    fun midSwipeTwoWidgetsAreOnScreenAndTheDotsFollowTheFinger() {
        show()
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, -height * 0.25f))
            moveBy(Offset(0f, -height * 0.25f))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).captureRoboImage()
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput { up() }
    }

    @Test
    fun aSwipeOnTheDotsStripSwitchesAPlatformWidgetByOne() {
        show()

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertEquals(listOf(2), shownEvents().map { it.index })
        assertEquals(2, widget.shownIndex)
    }

    @Test
    fun aSwipeDownOnTheStripGoesBackAndNeverPastTheFirstWidget() {
        widget = fixtureStack(shown = 0)
        show()

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput {
            swipeDown()
        }
        composeRule.waitForIdle()

        assertTrue(shownEvents().toString(), shownEvents().isEmpty())
        assertEquals(0, widget.shownIndex)
    }

    @Test
    fun aSwipeOnTheBodyOfAPlatformWidgetIsLeftToTheWidget() {
        show()

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f)
        }
        composeRule.waitForIdle()

        assertTrue(shownEvents().toString(), shownEvents().isEmpty())
    }

    @Test
    fun aSwipeAnywhereOnRiffleOwnWidgetSwitchesIt() {
        widget = fixtureGlanceStack(shown = 0)
        show()

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f)
        }
        composeRule.waitForIdle()

        assertEquals(listOf(1), shownEvents().map { it.index })
    }

    @Test
    fun whenThePagesScrollUpAndDownTheStackSwipesSideways() {
        presentation = WidgetStackPresentation(pagesScrollVertically = true)
        widget = fixtureGlanceStack(shown = 0)
        show()

        // Up and down is the pager's: nothing happens.
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).performTouchInput {
            swipeUp(startY = height * 0.8f, endY = height * 0.2f)
        }
        composeRule.waitForIdle()
        assertTrue(shownEvents().toString(), shownEvents().isEmpty())

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).performTouchInput {
            swipeLeft(startX = width * 0.8f, endX = width * 0.2f)
        }
        composeRule.waitForIdle()
        assertEquals(listOf(1), shownEvents().map { it.index })
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theDotsStripIsA48dpTargetAndTheStackIsOneNodeWithItsPosition() {
        show()

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).assertWidthIsAtLeast(48.dp)
        val stack = composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).fetchSemanticsNode().config
        assertEquals("Widget 2 of 3, Calendar", stack[SemanticsProperties.StateDescription])
        assertEquals(
            listOf("Next widget", "Previous widget", "Edit stack"),
            stack[SemanticsActions.CustomActions].map { it.label },
        )
        val strip = composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).fetchSemanticsNode().config
        assertEquals("Widgets in this stack", strip[SemanticsProperties.ContentDescription].single())
    }

    @Test
    fun theAccessibilityActionsMoveOneWidgetAndOpenTheEditor() {
        show()
        fun perform(label: String) =
            composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == label }.action()

        composeRule.runOnUiThread { perform("Next widget") }
        composeRule.waitForIdle()
        assertEquals(listOf(2), shownEvents().map { it.index })

        composeRule.runOnUiThread { perform("Previous widget") }
        composeRule.waitForIdle()
        assertEquals(listOf(2, 1), shownEvents().map { it.index })

        composeRule.runOnUiThread { perform("Edit stack") }
        assertTrue(actions.last() is LauncherShellAction.OpenWidgetStackEditor)
    }

    @Test
    fun anActionPastTheEndOfTheStackDoesNothing() {
        widget = fixtureStack(shown = 2)
        show()

        val handled =
            composeRule.onNodeWithTag(HOME_WIDGET_STACK_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == "Next widget" }
        var result: Boolean? = null
        composeRule.runOnUiThread { result = handled.action() }
        composeRule.waitForIdle()

        assertEquals(false, result)
        assertTrue(shownEvents().isEmpty())
    }

    @Test
    fun underReducedMotionTheStackJumpsToTheNextWidgetWithoutSettling() {
        presentation = WidgetStackPresentation(reducedMotion = true)
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(listOf(2), shownEvents().map { it.index })
    }

    @Test
    fun withMotionTheStackSettlesOnASpringAndReportsTheWidgetOnlyWhenItHasArrived() {
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput { swipeUp() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue("it arrived in two frames: ${shownEvents()}", shownEvents().isEmpty())

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(listOf(2), shownEvents().map { it.index })
    }

    @Test
    fun onlyTheShownWidgetAndItsNeighboursHoldAView() {
        widget = fixtureStack(labels = listOf("A", "B", "C", "D", "E", "F"), shown = 3)
        show()

        assertEquals(listOf(3, 4, 5).sorted(), viewsCreatedFor.distinct().sorted())
    }

    @Test
    fun swipingDoesNotRecomposeTheStack() {
        show()
        val before = probe.count(homeWidgetStackProbeKey("stack-1"))

        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput {
            down(center)
            repeat(DRAG_STEPS) { step ->
                moveBy(Offset(0f, -height * 0.4f / DRAG_STEPS), delayMillis = 16)
                if (step % 2 == 0) advanceEventTime(16)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val during = probe.count(homeWidgetStackProbeKey("stack-1"))
        composeRule.onNodeWithTag(HOME_WIDGET_STACK_DOTS_TEST_TAG).performTouchInput { up() }

        assertEquals("a swipe in flight recomposed the stack", before, during)
    }

    private companion object {
        const val STACK_SIZE_DP = 176
        const val DRAG_STEPS = 12
    }
}
