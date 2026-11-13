package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
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
 * The pinned run keeps its icons at the configured size and scrolls when it is longer than its room -- the dynamic side
 * beside it takes only its own entries and never shrinks them, and the bar is one icon plus its chrome tall.
 */
@RunWith(AndroidJUnit4::class)
class DockPinnedRunScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pins = List(8) { index -> shortcut("pin$index") }
    private var density = 1f

    @Test
    fun pinsKeepTheirSizeBesideTheDynamicSideAndTheBarKeepsItsHeight() {
        setContent(entries = listOf(tileEntry))

        val first = boundsOf(dockItemTestTag(pins.first().id))
        val surface = boundsOf(HOME_DOCK_SURFACE_TEST_TAG)

        assertEquals(ICON_DP * density, first.width, TOLERANCE_PX)
        assertEquals(ICON_DP * density, first.height, TOLERANCE_PX)
        assertEquals(dockCrossAxisDp(ICON_DP) * density, surface.height, TOLERANCE_PX)
    }

    @Test
    fun theLastPinIsReachedByScrollingNotBySqueezing() {
        setContent(entries = listOf(tileEntry))
        val surface = boundsOf(HOME_DOCK_SURFACE_TEST_TAG)

        // The run is longer than its viewport: the last pin is laid out past the dock's own edge until scrolled to.
        // `boundsInRoot` is clipped to the viewport and collapses to an empty rect for a pin that is fully clipped, so the
        // pin's laid-out position is what says where it is.
        val lastTag = dockItemTestTag(pins.last().id)
        assertTrue(
            "The last pin should start past the dock's edge before scrolling",
            composeRule.onNodeWithTag(lastTag).fetchSemanticsNode().positionInRoot.x >= surface.right - TOLERANCE_PX,
        )
        composeRule.onNodeWithTag(lastTag).performScrollTo()
        composeRule.waitForIdle()
        // Scrolling brings it fully inside the dock and does not resize anything.
        val last = boundsOf(lastTag)
        assertTrue("The last pin should be inside the dock after scrolling", last.right <= surface.right + TOLERANCE_PX)
        assertEquals(ICON_DP * density, last.width, TOLERANCE_PX)
        assertEquals(ICON_DP * density, last.height, TOLERANCE_PX)
    }

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun setContent(entries: List<DockDynamicEntry>) {
        composeRule.setContent {
            density = LocalDensity.current.density
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 200.dp)) {
                    Dock(
                        dock = DockModel(capacity = 8, items = pins, iconSizeDp = ICON_DP, itemSpacingDp = 10),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        position = DockPosition.BOTTOM,
                        interactions = DockInteractions(onAction = {}),
                        dynamicEntries = entries,
                    )
                }
            }
        }
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

    private val tileEntry =
        DockDynamicEntry(
            key = "ctx:now",
            label = "Now",
            identity = null,
            badgeCount = 0,
            isSelected = false,
            contentDescription = "Now",
            intent = DockDynamicEntryIntent.Delegate,
            symbol = DockDynamicSymbol.Glyph(StreamSection.NOW.glyph()),
        )

    private companion object {
        private const val ICON_DP = 56
        private const val TOLERANCE_PX = 1f
    }
}
