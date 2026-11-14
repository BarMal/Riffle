package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockDynamicEntry
import com.riffle.app.launcher.DockDynamicEntryIntent
import com.riffle.app.launcher.DockDynamicSymbol
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.stream.STREAM_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.glyph
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * The Dock's contextual mode (ADR 0003, Amendment 3) on the JVM: its one row scrolls to keep the ringed icon in view
 * (the Dock composable with many entries in a narrow box), and, through the real [HomeDestination] with the Stream
 * showing, a tile per page, a tap that switches pages, and a long-press that offers the page options the page header
 * used
 * to hold, each applied by the Stream with its Undo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockContextualModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    // The row on its own ------------------------------------------------------------------------------------------

    private val pins = List(6) { index -> shortcut("pin$index") }
    private val sections = StreamSection.layoutSections.filter { it != StreamSection.NOTIFICATIONS }
    private var openKey by mutableStateOf<String?>(null)
    private val delegated = mutableListOf<String>()

    private fun tileEntry(
        section: StreamSection,
        selected: Boolean,
    ) = DockDynamicEntry(
        key = "ctx:${section.storedId}",
        label = section.name.lowercase().replaceFirstChar { it.uppercase() },
        identity = null,
        badgeCount = 0,
        isSelected = selected,
        contentDescription = section.name,
        intent = DockDynamicEntryIntent.Delegate,
        symbol = DockDynamicSymbol.Glyph(section.glyph()),
        openLabel = "Show ${section.name}",
    )

    private fun showRow(position: DockPosition = DockPosition.BOTTOM) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 420.dp)) {
                    Dock(
                        dock = DockModel(capacity = 6, items = pins, iconSizeDp = 56, itemSpacingDp = 10),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        position = position,
                        interactions = DockInteractions(reducedMotion = true, onAction = {}),
                        dynamicEntries =
                            sections.map {
                                    section ->
                                tileEntry(section, "ctx:${section.storedId}" == openKey)
                            },
                        onDynamicEntryDelegated = { key -> delegated += key },
                        contextual = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tile(section: StreamSection) =
        composeRule.onNodeWithTag(
            dockDynamicSectionTileTestTag(section.name.lowercase().replaceFirstChar { it.uppercase() }),
        )

    private fun itemTags() =
        pins.map { dockItemTestTag(it.id) } +
            sections.map { dockDynamicSectionTileTestTag(it.name.lowercase().replaceFirstChar { c -> c.uppercase() }) }

    private fun visibleItemBounds() =
        itemTags().mapNotNull { tag ->
            val node = composeRule.onNodeWithTag(tag)
            val shown = runCatching { node.assertIsDisplayed() }.isSuccess
            if (shown) node.fetchSemanticsNode().boundsInRoot else null
        }

    @Test
    fun aPhoneWideRowShowsSixWholeItemsOfOneSizeAndTheRestScrollsAway() {
        showRow()
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val rowLeft = composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNode().boundsInRoot.left
        val whole = visibleItemBounds().filter { it.width >= 47f * density }
        assertTrue("fewer than six whole items on show: ${visibleItemBounds()}", whole.size >= 6)
        // Pinned icons and tiles read as one row of equal items: every whole one is the 48 dp target, never larger.
        whole.forEach { assertEquals(48f * density, it.width, 1f) }
        assertEquals(rowLeft, whole.first().left, 1f)
        composeRule.captureScreen()
    }

    @Test
    fun theRowScrollsOnlyAsFarAsItTakesToShowTheOpenPagesTileWhole() {
        openKey = "ctx:${sections.last().storedId}"
        showRow()
        val row = composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNode()
        val tile = tile(sections.last()).fetchSemanticsNode()
        // Wholly in view (the row's own end padding may leave room after it).
        assertTrue("the tile is clipped: ${tile.boundsInRoot} in ${row.boundsInRoot}", tile.boundsInRoot.width >= 47f)
        assertTrue(tile.boundsInRoot.right <= row.boundsInRoot.right + 1f)
    }

    @Test
    fun theRowIsOneScrollingRunOfThePinnedIconsAndTheTiles() {
        showRow()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
        // Six 56 dp pins and five tiles do not fit in 360 dp: the first pin shows and the last tile lies past the edge.
        composeRule.onNodeWithTag(dockItemTestTag(pins.first().id)).assertIsDisplayed()
        tile(sections.last()).assertIsNotDisplayed()
    }

    @Test
    fun theRowScrollsToTheRingedTileAndBackWhenTheRingMoves() {
        openKey = null
        showRow()
        tile(sections.last()).assertIsNotDisplayed()

        composeRule.runOnUiThread { openKey = "ctx:${sections.last().storedId}" }
        composeRule.waitForIdle()
        tile(sections.last()).assertIsDisplayed()

        composeRule.runOnUiThread { openKey = "ctx:${sections.first().storedId}" }
        composeRule.waitForIdle()
        tile(sections.first()).assertIsDisplayed()
    }

    @Test
    fun theRowScrollsAlongASideDockToo() {
        openKey = "ctx:${sections.last().storedId}"
        showRow(DockPosition.LEFT)
        tile(sections.last()).assertIsDisplayed()
    }

    @Test
    fun aDragAlongTheRunScrollsTheRowAndATapOnATileReportsItsKey() {
        showRow()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).performTouchInput {
            down(center)
            repeat(8) { moveBy(androidx.compose.ui.geometry.Offset(-60f, 0f), delayMillis = 16) }
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(dockItemTestTag(pins.first().id)).assertIsNotDisplayed()

        val section = sections.last()
        tile(section).performClick()
        composeRule.runOnIdle { assertEquals(listOf("ctx:${section.storedId}"), delegated) }
    }

    // Through the real Dock and Stream ----------------------------------------------------------------------------

    private lateinit var host: StreamHost
    private val actions = mutableListOf<LauncherShellAction>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dock-contextual").apply { isDaemon = true } }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun showStream() {
        // The real Stream wiring, so every section leaves Loading and a tile can land on its page.
        host = realWiringStreamHost(executor = executor)
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = true,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Recents")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private val layout get() = host.layouts.layoutFor(HomeLayoutDeviceClass.PHONE)

    @Test
    fun everyPageThatIsNotAnAppHasATileAndTheOpenOneIsRinged() {
        showStream()
        composeRule.onNodeWithTag(STREAM_SURFACE_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
        listOf("Now", "Recents", "Today", "Feeds").forEach { label ->
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().let {
                assertTrue("no tile for $label", it.isNotEmpty())
            }
        }
        assertEquals("now", host.dock.currentKey)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Now")).assertIsSelectedTile()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsSelectedTile() = assertIsSelected()

    @Test
    fun aTapOnATileSwitchesThePageAndMovesTheRing() {
        showStream()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Today")).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == "today" }
        composeRule.waitForIdle()
        assertEquals("today", host.dock.currentKey)
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Today")).assertIsSelectedTile()
        assertTrue("a tile launches nothing: $actions", actions.isEmpty())
    }

    @Test
    fun aLongPressOffersShowAsMoveAndHideAndShowAsIsAppliedWithUndo() {
        showStream()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Recents")).performScrollTo()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Recents")).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Show as List").assertIsDisplayed()
        composeRule.onNodeWithText("Move earlier").assertIsDisplayed()
        composeRule.onNodeWithText("Move later").assertIsDisplayed()
        composeRule.onNodeWithText("Hide section").assertIsDisplayed()
        composeRule.captureScreen()

        composeRule.onNodeWithText("Show as List").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.RECENTS))
        composeRule.onNodeWithText("Recents shown as List").assertIsDisplayed()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.RECENTS))
    }

    @Test
    fun aTileCanBeMovedEarlierAndLater() {
        showStream()
        val before = tileOrder()
        assertTrue("Recents leads Today by default: $before", before.indexOf("Recents") < before.indexOf("Today"))

        longPressAndChoose("Today", "Move earlier")
        val after = tileOrder()
        assertTrue("Today did not move before Recents: $after", after.indexOf("Today") < after.indexOf("Recents"))

        longPressAndChoose("Today", "Move later")
        assertEquals(before, tileOrder())
    }

    @Test
    fun aSectionCanBeHiddenAndOfferedBackFromAnotherTile() {
        showStream()
        longPressAndChoose("Today", "Hide section")
        assertTrue(StreamSection.TODAY !in layout.enabledSections.map { it.section })
        composeRule.onNodeWithText("Today hidden").assertIsDisplayed()
        composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Today")).fetchSemanticsNodes().let {
            assertTrue("the hidden section still has a tile", it.isEmpty())
        }

        longPressAndChoose("Recents", "Show Today")
        assertTrue(layout.layoutOf(StreamSection.TODAY).enabled)
    }

    /** The labels of the tiles in the row, left to right: by where each is laid out (clipped bounds say nothing). */
    private fun tileOrder(): List<String> =
        listOf("Now", "Notifications", "Recents", "Today", "Feeds")
            .mapNotNull { label ->
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes()
                    .firstOrNull()?.let { node -> label to node.positionInRoot.x }
            }
            .sortedBy { it.second }
            .map { it.first }

    private fun longPressAndChoose(
        tileLabel: String,
        item: String,
    ) {
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(tileLabel)).performScrollTo()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(tileLabel)).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(item).performClick()
        composeRule.waitForIdle()
    }

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )
}
