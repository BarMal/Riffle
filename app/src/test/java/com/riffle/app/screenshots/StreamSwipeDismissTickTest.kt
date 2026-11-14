package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A dismissal by swipe plays one light tick through the Stream's haptics (the wrapper that honours the "light ticks"
 * setting silences it) and nothing else does; a swipe that settles back, or on a notification that cannot be
 * dismissed, plays none (device fixes, round 5, item 2). Through the stack with the surface's own callbacks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamSwipeDismissTickTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ticks = mutableListOf<LauncherHapticEvent>()
    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()
    private val haptics =
        object : LauncherHaptics {
            override fun longPress() = Unit

            override fun perform(event: LauncherHapticEvent) {
                ticks += event
            }
        }

    private fun show(firstDismissible: Boolean = true) {
        val first =
            StreamFixtures.chatNotification.copy(
                title = "First",
                thread = emptyList(),
                actions =
                    if (firstDismissible) {
                        setOf(NotificationAction.OPEN, NotificationAction.DISMISS)
                    } else {
                        setOf(NotificationAction.OPEN)
                    },
            )
        val second = StreamFixtures.chatNotificationTwo.copy(title = "Second")
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(listOf(StreamGroup("chat:personal", "Chat", listOf(first, second))))
                )
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherHaptics provides haptics) {
                ScreenshotBackdrop {
                    Box(Modifier.fillMaxSize()) {
                        StreamSurface(
                            layout = StreamLayout.DEFAULT,
                            results = results,
                            env = StreamFixtures.environment(reducedMotion = true),
                            callbacks = StreamCallbacks(onAction = { content, action -> actions += content to action }),
                            initialPageKey = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipeFirst(
        dxDp: Float,
        millis: Long,
    ) {
        composeRule.onNodeWithText("First").performTouchInput {
            swipe(center, center + Offset(dxDp.dp.toPx(), 0f), durationMillis = millis)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aDismissalBySwipePlaysOneLightTickAndAsksTheStreamToDismiss() {
        show()
        swipeFirst(250f, 300)
        assertEquals(listOf(LauncherHapticEvent.DETENT), ticks)
        assertEquals(listOf(StreamAction.Dismiss), actions.map { it.second })
        assertTrue((actions.single().first as NotificationContent).title == "First")
    }

    @Test
    fun aSwipeThatSettlesBackPlaysNoTickAndSendsNothing() {
        show()
        swipeFirst(40f, 800)
        assertTrue("no tick: $ticks", ticks.isEmpty())
        assertTrue("nothing sent: $actions", actions.isEmpty())
    }

    @Test
    fun aNotificationThatCannotBeDismissedPlaysNoTickAndSendsNothing() {
        show(firstDismissible = false)
        swipeFirst(250f, 300)
        assertTrue("no tick: $ticks", ticks.isEmpty())
        assertTrue("nothing sent: $actions", actions.isEmpty())
    }
}
