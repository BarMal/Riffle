package com.riffle.app.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.expressions.FakeExpressionImageLoader
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val emptyRegistry =
        object : SourceRegistry {
            override fun descriptors(): List<SourceDescriptor> = emptyList()

            override fun source(id: SourceId): ItemSource? = null
        }

    private fun host() =
        StreamHost(
            provider =
                StreamContentProvider(
                    emptyRegistry,
                    context = { StreamContext(nowEpochMillis = STREAM_TEST_NOW) },
                ),
            layouts =
                CachedStreamLayoutRepository(
                    object : StreamLayoutStorePort {
                        override suspend fun read() = null

                        override suspend fun write(layouts: com.riffle.core.domain.launcher.stream.StreamLayouts) = Unit
                    },
                    CoroutineScope(Dispatchers.Unconfined + Job()),
                ),
            actions = { _, _ -> com.riffle.core.domain.launcher.stream.StreamActionResult.NotOffered },
            imageLoader = FakeExpressionImageLoader(),
            formats = FixedStreamFormats(),
        )

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
        composeRule.onNodeWithText("Stream").assertIsDisplayed()
        // The Dock is still there, drawn under it.
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun homeShowsNoStream() {
        show(streamShown = false)
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun theDockOffersOpenStreamAndItOpensIt() {
        show(streamShown = false)
        assertTrue("Open Stream" in dockActionLabels())
        invokeAction("Open Stream")
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), actions)
    }

    @Test
    fun whileTheStreamIsOpenTheDockOffersBackToHome() {
        show(streamShown = true)
        assertTrue("Back to Home" in dockActionLabels())
        invokeAction("Back to Home")
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), actions)
    }

    @Test
    fun withoutAStreamThereIsNoOpenStreamAction() {
        show(streamShown = false, stream = null)
        assertTrue("Open Stream" !in dockActionLabels())
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

private const val STREAM_TEST_NOW = 1_767_225_600_000L
