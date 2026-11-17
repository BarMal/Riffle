package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockInteractions
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Dock with three app pairs, two of them the same two apps in both orders (first app on the left of the tile, so
 * the order shows), on a bottom and a side edge, light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockPairScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pairsBottom() {
        render(DockPosition.BOTTOM)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun pairsBottomDark() {
        render(DockPosition.BOTTOM)
    }

    @Test
    fun pairsSide() {
        render(DockPosition.LEFT)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun pairsSideDark() {
        render(DockPosition.LEFT)
    }

    private fun pair(
        id: String,
        first: com.riffle.core.domain.launcher.apps.InstalledApp,
        second: com.riffle.core.domain.launcher.apps.InstalledApp,
    ) = AppPairItem(
        LauncherItemId(id),
        ScreenshotFixtures.shortcut(first).copy(id = LauncherItemId("$id-first")),
        ScreenshotFixtures.shortcut(second).copy(id = LauncherItemId("$id-second")),
    )

    private fun render(position: DockPosition) {
        val items =
            listOf(
                pair("pair-1", ScreenshotFixtures.camera, ScreenshotFixtures.mail),
                pair("pair-2", ScreenshotFixtures.mail, ScreenshotFixtures.camera),
                pair("pair-3", ScreenshotFixtures.chat, ScreenshotFixtures.browser),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment =
                        if (position == DockPosition.LEFT) Alignment.CenterStart else Alignment.BottomCenter,
                ) {
                    Dock(
                        dock = DockModel(capacity = items.size, items = items, position = position),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = SolidColorAppIconLoader(),
                        position = position,
                        interactions = DockInteractions(position = position, onAction = {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.captureScreen()
    }
}
