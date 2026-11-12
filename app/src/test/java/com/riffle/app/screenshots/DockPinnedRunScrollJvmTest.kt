package com.riffle.app.screenshots

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
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockDynamicEntry
import com.riffle.app.launcher.DockDynamicEntryIntent
import com.riffle.app.launcher.DockDynamicSymbol
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.EmptyAppIconLoader
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.dockItemTestTag
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The device test `DockPinnedRunScrollTest` without an emulator: eight 56 dp pins beside the Stream button in a 400 dp
 * dock keep their size, the last one lies past the dock's edge until the run is scrolled to it, and scrolling brings it
 * fully inside without resizing anything. (`boundsInRoot` is clipped and collapses to an empty rect for a pin that is
 * fully clipped, so the laid-out position is what says where an unscrolled pin is.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPinnedRunScrollJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pins = List(8) { index -> shortcut("pin$index") }
    private var density = 1f

    @Test
    fun theLastPinIsReachedByScrollingNotBySqueezing() {
        setContent()
        val surface = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val lastTag = dockItemTestTag(pins.last().id)

        val laidOutAt = composeRule.onNodeWithTag(lastTag).fetchSemanticsNode().positionInRoot.x
        assertTrue(laidOutAt >= surface.right - TOLERANCE_PX)
        composeRule.onNodeWithTag(lastTag).performScrollTo()
        composeRule.waitForIdle()

        val last = composeRule.onNodeWithTag(lastTag).fetchSemanticsNode().boundsInRoot
        assertTrue(last.right <= surface.right + TOLERANCE_PX)
        assertEquals(ICON_DP * density, last.width, TOLERANCE_PX)
        assertEquals(ICON_DP * density, last.height, TOLERANCE_PX)
    }

    private fun setContent() {
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
                        dynamicEntries = listOf(streamEntry),
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

    private val streamEntry =
        DockDynamicEntry(
            key = "stream",
            label = "Stream",
            identity = null,
            badgeCount = 0,
            isSelected = false,
            contentDescription = "Open Stream",
            intent = DockDynamicEntryIntent.Delegate,
            symbol = DockDynamicSymbol.Stream,
        )

    private companion object {
        private const val ICON_DP = 56
        private const val TOLERANCE_PX = 1f
    }
}
