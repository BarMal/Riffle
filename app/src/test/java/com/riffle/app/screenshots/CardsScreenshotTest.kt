package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cards mode as the launcher shows it: the real [HomeDestination] -- stage surface plus the dock it
 * shares the screen with -- driven by fake shell state, across the posture matrix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CardsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun perAppStackCompact() {
        render(ScreenshotFixtures.cardsState(), AdaptiveStagePosture.UNKNOWN)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun perAppStackCompactDark() {
        render(ScreenshotFixtures.cardsState(), AdaptiveStagePosture.UNKNOWN)
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun perAppStackCompactLargeFont() {
        render(ScreenshotFixtures.cardsState(), AdaptiveStagePosture.UNKNOWN)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun perAppStackUnfolded() {
        render(ScreenshotFixtures.cardsState(HomeLayoutDeviceClass.FOLDABLE), AdaptiveStagePosture.UNFOLDED)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLETOP_FOLDABLE)
    fun perAppStackTabletop() {
        render(ScreenshotFixtures.cardsState(HomeLayoutDeviceClass.FOLDABLE), AdaptiveStagePosture.TABLETOP)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.TABLET_LANDSCAPE)
    fun perAppStackTabletLandscape() {
        render(ScreenshotFixtures.cardsState(HomeLayoutDeviceClass.TABLET), AdaptiveStagePosture.UNKNOWN)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun allNotificationsUnfolded() {
        render(
            state =
                ScreenshotFixtures.cardsState(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    launcherSettings = LauncherSettings(cards = CardsSettings(unfoldedShowAllNotifications = true)),
                ),
            posture = AdaptiveStagePosture.UNFOLDED,
            initialContext = AdaptiveStageInteractionContext(allNotificationsSelected = true),
        )
    }

    @Test
    fun emptyCompact() {
        render(ScreenshotFixtures.cardsState(notificationGroups = emptyList()), AdaptiveStagePosture.UNKNOWN)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun emptyUnfolded() {
        render(
            state =
                ScreenshotFixtures.cardsState(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    notificationGroups = emptyList(),
                ),
            posture = AdaptiveStagePosture.UNFOLDED,
        )
    }

    private fun render(
        state: LauncherShellState,
        posture: AdaptiveStagePosture,
        initialContext: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    ) {
        val iconLoader = SolidColorAppIconLoader()
        composeRule.setContent {
            // Hoisted the way LauncherShell hoists it, so the surface's own context updates stick.
            var context by remember { mutableStateOf(initialContext) }
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = iconLoader,
                    adaptiveStageWindowLayout = screenshotWindowLayout(posture),
                    adaptiveStageContext = context,
                    onAdaptiveStageContextChanged = { next -> context = next },
                    onAction = {},
                )
            }
        }
        composeRule.captureScreen()
    }
}
