package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * A live app arriving in, and leaving, the Dock's Home mode (batch 7, item 3) frame by frame: the body eases to its new
 * length in the same single motion as when the Stream opens (monotonic, no overshoot, about its middle, the pinned run
 * riding its start edge), and the live app's icon fades in as the body reaches it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockLiveAppsResizeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val watchers = CopyOnWriteArrayList<() -> Unit>()
    private val changes =
        SourceChangeSource { listener ->
            watchers += listener
            return@SourceChangeSource { watchers -= listener }
        }
    private val active = mutableListOf<LauncherNotification>()
    private val liveApp = ScreenshotFixtures.calendar

    private fun note() =
        LauncherNotification(
            key = LauncherNotificationKey("0|calendar|1"),
            packageName = AppPackageName(liveApp.identity.packageName.value),
            canDismiss = true,
            title = "Standup",
            text = "In ten minutes",
            messages = emptyList(),
            postedAtEpochMillis = StreamFixtures.NOW,
        )

    private fun show() {
        val host =
            realWiringStreamHost(
                executor = Executor { it.run() },
                activeNotificationsNow = { synchronized(active) { active.toList() } },
                notificationChanges = changes,
                installedApps = ScreenshotFixtures.installedApps,
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    onAction = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(SETTLE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
    }

    private fun change(edit: () -> Unit): List<DockSample> {
        composeRule.runOnUiThread {
            synchronized(active) { edit() }
            watchers.forEach { it() }
        }
        val samples = mutableListOf(composeRule.sampleDock())
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            samples += composeRule.sampleDock()
        }
        return samples
    }

    private val dp get() = composeRule.density.density

    @Test
    fun aLiveAppArrivingExtendsTheBodyInTheSameMotionAndLeavingRetractsIt() {
        show()
        val before = composeRule.sampleDock()
        assertEquals("no live app yet", 0, before.entries.size)

        val arriving = change { active += note() }
        val lengths = arriving.map { it.length(horizontal = true) }
        val log = lengths.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the live app did not arrive: $log", arriving.last().entries.size == 1)
        assertTrue("the body did not grow: $log", lengths.last() > lengths.first() + 20f * dp)
        lengths.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the body shrank while it grows ($a to $b)\n$log", b >= a - 1f)
        }
        assertTrue("it overshot\n$log", lengths.max() <= lengths.last() + 1f)
        assertTrue(
            "it jumped instead of easing\n$log",
            lengths.count { it > lengths.first() + 2f && it < lengths.last() - 2f } >= 3,
        )
        arriving.forEachIndexed { i, s ->
            assertEquals("frame $i: the middle moved", arriving.first().centre(true), s.centre(true), 2f)
            val pin = s.firstPin(true)!!.rect.left - s.start(true)
            assertEquals(
                "frame $i: the pinned run moved against the body",
                before.firstPin(true)!!.rect.left - before.start(true),
                pin,
                2f,
            )
        }
        // The live app's icon is never drawn outside the body, and fades in as the body reaches it.
        arriving.forEach { s ->
            val icon = s.entries.firstOrNull() ?: return@forEach
            if (icon.rect.left > s.body.right - 1f) assertTrue((icon.alpha ?: 0f) < 0.02f)
        }
        assertTrue(
            "the icon did not fade in with the body",
            arriving.any { s -> (s.entries.firstOrNull()?.alpha ?: 0f) in 0.05f..0.95f },
        )

        val leaving = change { active.clear() }
        val back = leaving.map { it.length(horizontal = true) }
        val backLog = back.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not retract: $backLog", back.last() < back.first() - 20f * dp)
        back.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the body grew while it retracts ($a to $b)\n$backLog", b <= a + 1f)
        }
        assertEquals("it is back to its first length", before.length(true), back.last(), 2f)
    }

    private companion object {
        const val FRAMES = 40
        const val SETTLE_FRAMES = 20
    }
}
