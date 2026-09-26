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
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
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
                ),
            posture = AdaptiveStagePosture.UNFOLDED,
            initialContext = AdaptiveStageInteractionContext(allNotificationsSelected = true),
        )
    }

    @Test
    fun allNotificationsCompact() {
        // #1212: "All" is always the dock selector's first entry, so it is reachable on a compact
        // window without switching anything on.
        render(
            state = ScreenshotFixtures.cardsState(),
            posture = AdaptiveStagePosture.UNKNOWN,
            initialContext = AdaptiveStageInteractionContext(allNotificationsSelected = true),
        )
    }

    @Test
    fun pinnedEmptyStageCompact() {
        val mapsStage =
            AppStageId(
                ScreenshotFixtures.maps.identity.packageName,
                ScreenshotFixtures.maps.identity.profile.id,
            )
        val phoneCards = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE, HomeLayoutDeviceClass.PHONE)
        val preferences = AppStagePreferences(pinnedStageIds = listOf(mapsStage), selectedStageId = mapsStage)
        render(
            state =
                ScreenshotFixtures.cardsState(
                    launcherSettings =
                        LauncherSettings(
                            cards = CardsSettings(stagePreferencesByLayout = mapOf(phoneCards to preferences)),
                        ),
                ),
            posture = AdaptiveStagePosture.UNKNOWN,
        )
    }

    @Test
    fun noNotificationAccessCompact() {
        render(
            state =
                ScreenshotFixtures.cardsState(
                    notificationGroups = emptyList(),
                    notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                ),
            posture = AdaptiveStagePosture.UNKNOWN,
        )
    }

    @Test
    fun stageSpineEnabledCompact() {
        render(
            state =
                ScreenshotFixtures.cardsState(
                    launcherSettings = LauncherSettings(cards = CardsSettings(showStageSpine = true)),
                ),
            posture = AdaptiveStagePosture.UNKNOWN,
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
