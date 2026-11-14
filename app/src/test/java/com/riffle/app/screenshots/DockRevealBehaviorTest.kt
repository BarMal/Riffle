package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.toSize
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.stream.StreamFocusTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * The Dock's row does not move when the open page changes to an icon that is already wholly in view, and when it is
 * not, moves only enough to show it whole. Through the real [HomeDestination] with the Stream open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockRevealBehaviorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dock-reveal").apply { isDaemon = true } }
    private var shellShown by mutableStateOf(true)
    private lateinit var host: StreamHost

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun isDockIcon(tag: String?) =
        tag != null && (tag.startsWith("dock-item:") || tag.startsWith("dock-dynamic-entry:"))

    private fun icons(): Map<String, SemanticsNode> =
        composeRule.onAllNodes(
            SemanticsMatcher("a Dock icon") { isDockIcon(it.config.getOrNull(SemanticsProperties.TestTag)) },
        ).fetchSemanticsNodes().associateBy { it.config[SemanticsProperties.TestTag] }

    private fun whole(node: SemanticsNode) = Rect(node.positionInRoot, node.size.toSize())

    private fun viewport(): Rect = whole(composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNode())

    private fun fullyVisible(node: SemanticsNode): Boolean {
        val port = viewport()
        val item = whole(node)
        return item.left >= port.left - 0.5f && item.right <= port.right + 0.5f &&
            item.top >= port.top - 0.5f && item.bottom <= port.bottom + 0.5f
    }

    private fun boundsOfAll() = icons().mapValues { (_, node) -> whole(node) }

    private fun show() {
        host = realWiringStreamHost(executor = executor)
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = shellShown,
                    onAction = { action -> if (action == LauncherShellAction.OpenHome) shellShown = false },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey != null && host.dock.pages.size > 3 }
        composeRule.waitForIdle()
    }

    @Test
    fun selectingAnIconThatIsAlreadyWhollyVisibleMovesNeitherTheRowNorAnyIcon() {
        show()
        val before = boundsOfAll()
        val visible =
            icons().filter { (tag, node) -> tag.startsWith("dock-dynamic-entry:") && fullyVisible(node) }.keys
        assertTrue("some tiles are in view: ${before.keys}", visible.size >= 2)
        // Open each visible tile in turn by a tap: the row must not move for any of them.
        for (tag in visible) {
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.waitForIdle()
            assertEquals("tapping $tag moved the Dock's icons", before, boundsOfAll())
        }
    }

    @Test
    fun anIconThatIsClippedOrOffScreenComesWholeIntoViewWhenItsPageOpens() {
        show()
        val lastTile = host.dock.pages.last { page -> !page.isAppPage }
        val last = host.dock.pages.first { page -> page.section == lastTile.section }
        val clipped =
            icons().entries.lastOrNull { (tag, node) ->
                tag.startsWith("dock-dynamic-entry:") && !fullyVisible(node)
            }
        assertNotNull("the last tiles lie past the row's end at the start", clipped)
        val before = boundsOfAll()
        host.focus.request(StreamFocusTarget.Section(last.section))
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == last.key }
        composeRule.waitForIdle()
        val ringed = icons().entries.first { (tag, _) -> tag == clipped!!.key }.value
        assertTrue("the opened page's tile is wholly in view", fullyVisible(ringed))
        assertTrue("the row moved to show it", before != boundsOfAll())
    }
}
