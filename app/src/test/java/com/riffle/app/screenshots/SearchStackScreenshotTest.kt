package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.search.SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * What the search stack's screenshot tests share: the fixtures, the stream host that holds one chat notification, and
 * the capture. The folded and the unfolded classes differ only in their device.
 */
internal abstract class SearchStackShots {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    protected val executor = Executors.newSingleThreadExecutor { Thread(it, "search-shots").apply { isDaemon = true } }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    protected val maps = ScreenshotFixtures.maps
    protected val notes = ScreenshotFixtures.notes
    protected val clock = ScreenshotFixtures.clock
    protected val weather = ScreenshotFixtures.weather
    protected val chat = ScreenshotFixtures.chat

    protected fun host(): StreamHost =
        realWiringStreamHost(
            executor = executor,
            activeNotifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("0|chat|1"),
                        packageName = AppPackageName(chat.identity.packageName.value),
                        title = "Sam",
                        text = "Are we still on for lunch tomorrow?",
                        postedAtEpochMillis = StreamFixtures.NOW - MINUTE,
                    ),
                ),
            installedApps = ScreenshotFixtures.installedApps,
        )

    protected fun searching(
        query: String,
        results: List<InstalledApp>,
        deviceClass: HomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
        dock: DockPosition? = null,
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState(deviceClass, dock).copy(
                destination = ShellDestination.SEARCH,
                searchQuery = query,
                searchResults = results,
                appShortcutsByApp = StreamFixtures.shortcutsOf(maps),
            )
        val settings = base.launcherSettings
        return base.copy(
            launcherSettings =
                settings.copy(motion = settings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON)),
        )
    }

    protected fun shoot(
        state: LauncherShellState,
        notificationLine: Boolean = false,
    ) {
        val stream = host()
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = stream,
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
        if (notificationLine) {
            composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
                composeRule.onAllNodes(hasTestTag(SEARCH_STACK_NOTIFICATION_LINE_TEST_TAG), useUnmergedTree = true)
                    .fetchSemanticsNodes().any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith("Sam") } == true
                    }
            }
        }
        composeRule.captureScreen()
    }

    protected val results get() = listOf(maps, chat, notes, clock, weather)

    protected companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}

/** Folded: a compact phone with a bottom Dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
internal class SearchStackScreenshotTest : SearchStackShots() {
    @Test
    fun foldedWithResultsLight() = shoot(searching("m", results))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun foldedWithResultsDark() = shoot(searching("m", results))

    @Test
    fun foldedWithANotificationOnTheFrontCardLight() =
        shoot(searching("c", listOf(chat, maps, notes)), notificationLine = true)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun foldedWithANotificationOnTheFrontCardDark() =
        shoot(searching("c", listOf(chat, maps, notes)), notificationLine = true)

    @Test
    fun foldedWithNoResultsLight() = shoot(searching("zzz", emptyList()))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun foldedWithNoResultsDark() = shoot(searching("zzz", emptyList()))

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun foldedWithResultsLargeFont() = shoot(searching("m", results))
}

/** Unfolded: the foldable's inner display, the stack in the Stream column's rectangle beside a right Dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
internal class SearchStackUnfoldedScreenshotTest : SearchStackShots() {
    @Test
    fun unfoldedWithResultsLight() = shoot(searching("m", results, HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedWithResultsDark() = shoot(searching("m", results, HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT))

    @Test
    fun unfoldedWithNoResultsLight() =
        shoot(searching("zzz", emptyList(), HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedWithNoResultsDark() =
        shoot(searching("zzz", emptyList(), HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT))
}
