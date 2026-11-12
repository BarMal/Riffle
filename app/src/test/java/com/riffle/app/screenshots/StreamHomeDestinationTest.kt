package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_SPINE_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Stream through the real [HomeDestination]: a full surface that replaces Home while the Dock stays drawn under it,
 * the Dock's accessibility action to open it and to go back, and no Stream when the shell has none.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamHomeDestinationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()

    private fun host() = emptyStreamHost()

    private fun show(
        streamShown: Boolean,
        stream: StreamHost? = host(),
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    streamShown = streamShown,
                    onAction = { actions += it },
                )
            }
        }
    }

    private fun dockActionLabels(): List<String> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNodes()
            .flatMap { it.config[SemanticsActions.CustomActions] }
            .map { it.label }

    @Test
    fun theStreamReplacesHomeAndTheDockStaysUnderIt() {
        show(streamShown = true)
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        // The Stream is a pager of pages with its spine above it.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_SPINE_TEST_TAG).assertIsDisplayed()
        // The Dock is still there, drawn under it.
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun homeShowsNoStream() {
        show(streamShown = false)
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theDockOffersOpenStreamAndOpenLibraryAndTheyOpenThem() {
        show(streamShown = false)
        assertTrue("Open Stream" in dockActionLabels())
        assertTrue("Open Library" in dockActionLabels())
        invokeAction("Open Library")
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenAppDrawer), actions)
        actions.clear()
        invokeAction("Open Stream")
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun whileTheStreamIsOpenTheDockOffersBackToHomeAndStillOffersTheLibrary() {
        show(streamShown = true)
        assertTrue("Back to Home" in dockActionLabels())
        assertTrue("Open Stream" !in dockActionLabels())
        assertTrue("Open Library" in dockActionLabels())
        invokeAction("Back to Home")
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun theStreamButtonIsTheDocksLastDynamicEntryAndTogglesTheStream() {
        show(streamShown = false)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).assertIsDisplayed()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun theStreamButtonReturnsHomeWhileTheStreamIsOpen() {
        show(streamShown = true)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun withoutAStreamThereIsNoOpenStreamAction() {
        show(streamShown = false, stream = null)
        assertTrue("Open Stream" !in dockActionLabels())
        assertTrue("Open Library" in dockActionLabels())
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.TestTag,
                STREAM_SURFACE_TEST_TAG,
            ),
        )
            .assertCountEquals(0)
    }

    private fun invokeAction(label: String) {
        composeRule.runOnUiThread {
            val action =
                composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
                    .fetchSemanticsNodes()
                    .flatMap { it.config[SemanticsActions.CustomActions] }
                    .first { it.label == label }
            action.action()
        }
    }
}
