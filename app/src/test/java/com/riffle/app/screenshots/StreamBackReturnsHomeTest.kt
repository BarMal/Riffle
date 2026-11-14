package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.core.domain.launcher.ShellDestination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * The way out of the Stream that needs no gesture: the system Back (the gesture or the button) while the Stream shows
 * asks the shell for Home, through the real [LauncherShellContent] with the real Stream wiring.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamBackReturnsHomeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "stream-back").apply { isDaemon = true } }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    @Test
    fun systemBackWhileTheStreamShowsReturnsHome() {
        val sent = mutableListOf<LauncherShellAction>()
        val host = realWiringStreamHost(executor = executor)
        val state = ScreenshotFixtures.homeState().copy(destination = ShellDestination.STREAM)
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    onAction = { action -> sent += action },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }
}
