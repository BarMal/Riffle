package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.stream.glyph
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.stream.StreamSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Dock's contextual mode (ADR 0003, Amendment 3) on a real touch pipeline: pinned icons, live entries and a tile per
 * Stream page are one row that scrolls along the run with the platform's fling, keeps the ringed icon in view, and every
 * tile is a 48 dp target with a spoken name, a click action and a long-press action for the page options. The page
 * ordering, the press that toggles the mode and the options are covered on the JVM and by [DockPressInteractionTest].
 * (Lazy and clipped nodes: displayed-ness is asserted, never `boundsInRoot`, which a parent clips.)
 */
@RunWith(AndroidJUnit4::class)
class DockContextualRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pins = List(6) { index -> shortcut("pin$index") }
    private val sections = StreamSection.layoutSections.filter { it != StreamSection.NOTIFICATIONS }
    private var openKey by mutableStateOf<String?>(null)
    private val delegated = mutableListOf<String>()

    private fun label(section: StreamSection) = section.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun tileEntry(section: StreamSection) =
        DockDynamicEntry(
            key = "ctx:${section.storedId}",
            label = label(section),
            identity = null,
            badgeCount = 0,
            isSelected = "ctx:${section.storedId}" == openKey,
            contentDescription = label(section),
            intent = DockDynamicEntryIntent.Delegate,
            symbol = DockDynamicSymbol.Glyph(section.glyph()),
            openLabel = "Show ${label(section)}",
            appMenu = listOf(ShortcutContextMenuItem(label = "Show as List", onSelect = {})),
        )

    /** What the pinned apps know of the Stream, or null (they only launch): set by the test before [show]. */
    private var pinnedStream: DockPinnedStream? = null
    private val launched = mutableListOf<LauncherShellAction>()

    private fun show(position: DockPosition = DockPosition.BOTTOM) {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 420.dp)) {
                    CompositionLocalProvider(LocalDockPinnedStream provides pinnedStream) {
                        Dock(
                            dock = DockModel(capacity = 6, items = pins, iconSizeDp = 56, itemSpacingDp = 10),
                            isEditing = false,
                            notificationGroupsByApp = emptyList(),
                            appShortcutsByApp = emptyMap(),
                            appIconLoader = EmptyAppIconLoader,
                            widgetViewFactory = EmptyHomeWidgetViewFactory,
                            position = position,
                            interactions = DockInteractions(reducedMotion = true, onAction = { launched += it }),
                            dynamicEntries = sections.map(::tileEntry),
                            onDynamicEntryDelegated = { key -> delegated += key },
                            contextual = true,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tile(section: StreamSection) = composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(label(section)))

    @Test
    fun theRowHoldsThePinsAndTheTilesAndTheLastTileLiesPastTheEdge() {
        show()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
        composeRule.onNodeWithTag(dockItemTestTag(pins.first().id)).assertIsDisplayed()
        tile(sections.last()).assertIsNotDisplayed()
    }

    @Test
    fun aSwipeAlongTheRunScrollsTheRowAndATapOnATileReportsItsKey() {
        show()
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(dockItemTestTag(pins.first().id)).assertIsNotDisplayed()

        val section = sections.last()
        tile(section).assertIsDisplayed()
        tile(section).performClick()
        composeRule.runOnIdle { assertEquals(listOf("ctx:${section.storedId}"), delegated) }
    }

    @Test
    fun theRowScrollsToTheRingedTileWhenTheRingMoves() {
        show()
        tile(sections.last()).assertIsNotDisplayed()
        composeRule.runOnUiThread { openKey = "ctx:${sections.last().storedId}" }
        composeRule.waitForIdle()
        tile(sections.last()).assertIsDisplayed()
        tile(sections.last()).assertIsSelected()
    }

    @Test
    fun theRowScrollsAlongASideDockToo() {
        openKey = "ctx:${sections.last().storedId}"
        show(DockPosition.LEFT)
        tile(sections.last()).assertIsDisplayed()
    }

    @Test
    fun everyTileIsA48DpTargetWithASpokenNameAClickActionAndALongPressAction() {
        openKey = "ctx:${sections.first().storedId}"
        show()
        val first = sections.first()
        tile(first).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val config = tile(first).fetchSemanticsNode().config
        assertEquals("Show ${label(first)}", config[SemanticsActions.OnClick].label)
        assertTrue(SemanticsActions.OnLongClick in config)
        tile(first).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.runOnIdle { assertEquals(listOf("ctx:${first.storedId}"), delegated) }
    }

    @Test
    fun inTheContextualModeATapOnAPinnedAppShowsItsPageAndTheOpenOneIsSelected() {
        val shown = mutableListOf<AppIdentity>()
        pinnedStream =
            DockPinnedStream(
                contextual = true,
                selectedKey = pinnedEntryKey(pins[1].appIdentity),
                labels = pins.associate { pinnedEntryKey(it.appIdentity) to it.label },
                onShow = { identity -> shown += identity },
            )
        show()
        composeRule.onNodeWithTag(dockItemTestTag(pins[0].id)).performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(pins[0].appIdentity), shown)
            assertTrue("a tap in the contextual mode launched the app: $launched", launched.isEmpty())
        }
        // The app whose page is open says so, and the others do not.
        composeRule.onNode(isSelected() and hasAnyAncestor(hasTestTag(dockItemTestTag(pins[1].id)))).assertExists()
        composeRule.onNode(
            isSelected() and hasAnyAncestor(hasTestTag(dockItemTestTag(pins[0].id))),
        ).assertDoesNotExist()
    }

    @Test
    fun withoutTheContextualModeATapOnAPinnedAppStillLaunchesIt() {
        pinnedStream =
            DockPinnedStream(
                contextual = false,
                selectedKey = null,
                labels = emptyMap(),
                onShow = { error("the Home mode launches pinned apps") },
            )
        show()
        composeRule.onNodeWithTag(dockItemTestTag(pins[0].id)).performClick()
        composeRule.runOnIdle { assertTrue("the tap did not launch: $launched", launched.isNotEmpty()) }
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
