package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.HomeWidgetPlaceholder
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalWidgetSetUpSupport
import com.riffle.app.launcher.ShortcutContextSurface
import com.riffle.app.launcher.WIDGET_STACK_EDITOR_TEST_TAG
import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.app.launcher.WidgetSetUpSupport
import com.riffle.app.launcher.WidgetStackEditorSurface
import com.riffle.app.launcher.WidgetStackMemberIcons
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.restacked
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A widget that could not be bound is set up from its placeholder (ADR 0003, Amendment 18), drawn for real. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PlaceholderSetUpScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private val provider = WidgetProviderIdentity(AppPackageName("com.example.widgets"), WidgetProviderClassName(".W"))
    private val installed =
        InstalledWidgetProvider(
            identity = provider,
            label = "Weather",
            dimensions = WidgetProviderDimensions(minWidthDp = 110, minHeightDp = 110),
        )
    private val span = GridSpan(2, 2)

    private fun placeholder(
        label: String = "Weather",
        withProvider: Boolean = true,
    ) = WidgetItem(
        id = LauncherItemId("widget:p"),
        appWidgetId = HostedWidgetId(0),
        label = label,
        placement = GridPlacement(GridCell(0, 0), span),
        provider = provider.takeIf { withProvider },
    )

    private fun stackWithPlaceholder(shown: Int) =
        placeholder().restacked(
            listOf(
                StackedWidget(HostedWidgetId(5), "Calendar"),
                StackedWidget(HostedWidgetId(0), "Weather", provider = provider),
            ),
            shown,
        )

    @Composable
    private fun Frame(
        tag: String,
        item: WidgetItem,
    ) {
        Box(modifier = Modifier.size(170.dp).testTag(tag)) {
            HomeWidgetPlaceholder(
                widget = item,
                isEditing = false,
                onAction = { actions += it },
                contextSurface = ShortcutContextSurface.HOME,
            )
        }
    }

    private fun show(
        support: WidgetSetUpSupport,
        vararg items: Pair<String, WidgetItem>,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalWidgetSetUpSupport provides support) {
                    Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { (tag, item) -> Frame(tag, item) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val ready get() = WidgetSetUpSupport(catalogReady = true, providers = listOf(installed))

    @Test
    fun tappingThePlaceholderRunsTheSetUpInPlaceAndTheThreeStatesRead() {
        show(
            ready,
            "can" to placeholder(),
            "unknown" to placeholder(label = "Notes", withProvider = false),
        )
        composeRule.onNodeWithText("Not set up. Tap to set up.").assertIsDisplayed()
        composeRule.onNodeWithText("Not set up. Add it again from Widgets.").assertIsDisplayed()
        composeRule.onNodeWithTag("can").captureRoboImage()

        composeRule.onNodeWithText("Not set up. Tap to set up.").assertHasClickAction()
        composeRule.onNodeWithText("Not set up. Tap to set up.").performClick()

        val request = actions.single() as LauncherShellAction.RequestAddWidget
        assertEquals(WidgetAddTarget.HOME_PLACEHOLDER, request.target)
        assertEquals(LauncherItemId("widget:p"), request.targetItemId)
        assertEquals(0, request.targetMemberIndex)
        assertEquals(provider, request.provider)
        assertEquals(installed.dimensions, request.dimensions)
    }

    @Test
    fun aPlaceholderWithNoProviderOffersNoSetUp() {
        show(ready, "unknown" to placeholder(withProvider = false))
        composeRule.onNodeWithTag("unknown").captureRoboImage()

        composeRule.onNodeWithText("Not set up. Add it again from Widgets.").performClick()

        assertTrue(actions.toString(), actions.isEmpty())
    }

    @Test
    fun aPlaceholderWhoseAppIsGoneSaysSoAndOffersNoSetUp() {
        show(WidgetSetUpSupport(catalogReady = true), "gone" to placeholder())
        composeRule.onNodeWithTag("gone").captureRoboImage()
        composeRule.onNodeWithText("Its app isn't installed. You can remove it.").assertIsDisplayed()

        composeRule.onNodeWithText("Its app isn't installed. You can remove it.").performClick()

        assertTrue(actions.toString(), actions.isEmpty())
    }

    @Test
    fun theSetUpIsAlsoAnAccessibilityAction() {
        show(ready, "can" to placeholder())

        val node = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        composeRule.runOnUiThread {
            node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Set up Weather" }
                .action()
        }

        assertTrue(actions.toString(), actions.single() is LauncherShellAction.RequestAddWidget)
    }

    @Test
    fun aPlaceholderMemberOfAStackIsSetUpByTappingItWhenShown() {
        show(ready, "stack" to stackWithPlaceholder(shown = 1))

        composeRule.onNodeWithText("Not set up. Tap to set up.").performClick()

        val request = actions.single() as LauncherShellAction.RequestAddWidget
        assertEquals(LauncherItemId("widget:p"), request.targetItemId)
        assertEquals(1, request.targetMemberIndex)
    }

    @Test
    fun theEditStackSheetOffersSetUpForAPlaceholderMemberOnly() {
        val stack = stackWithPlaceholder(shown = 0)
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalWidgetSetUpSupport provides ready) {
                    WidgetStackEditorSurface(
                        stack = stack,
                        layout =
                            HomeLayoutDefaults.standard().let {
                                it.copy(
                                    pages =
                                        listOf(
                                            LauncherPage(
                                                id = LauncherPageId("home"),
                                                grid = GridDimensions(4, 4),
                                                items = listOf(stack),
                                            ),
                                        ),
                                )
                            },
                        icons = WidgetStackMemberIcons { null },
                        onAction = { actions += it },
                        onDismiss = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIDGET_STACK_EDITOR_TEST_TAG).captureRoboImage()

        composeRule.onAllNodesWithText("Set up widget").assertCountEquals(1)
        composeRule.onNodeWithText("Set up widget").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Set up widget").performClick()

        val request = actions.single() as LauncherShellAction.RequestAddWidget
        assertEquals(1, request.targetMemberIndex)
    }
}
