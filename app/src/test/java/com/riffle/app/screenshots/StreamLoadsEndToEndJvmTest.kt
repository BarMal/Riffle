package com.riffle.app.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.stream.StreamDestination
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.streamSpineMarkerTestTag
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.stream.StreamLoadingRule
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors

/**
 * The Stream through its REAL wiring: the production registry assembly (`streamSourceRegistry` over the built-in
 * sources, `enabledStreamRegistry`, `StreamContentProvider`, the real `StreamDestination` and its subscriptions) with
 * only the platform services faked, on a real background thread like the device's. Every section must leave Loading.
 * (The owner's phone showed "Loading" on every page for good: the fixture-driven surface tests never saw it.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamLoadsEndToEndJvmTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor =
        Executors.newSingleThreadExecutor { Thread(it, "test-item-sources").apply { isDaemon = true } }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun host(
        calendar: SourceAccess,
        notifications: NotificationAccessStatus,
        usage: SourceAccess,
        loadingTimeoutMillis: Long = StreamLoadingRule.TIMEOUT_MILLIS,
    ): StreamHost =
        realWiringStreamHost(
            executor = executor,
            calendar = calendar,
            notifications = notifications,
            usage = usage,
            loadingTimeoutMillis = loadingTimeoutMillis,
        )

    private fun show(host: StreamHost) {
        composeRule.setContent {
            ScreenshotBackdrop {
                StreamDestination(
                    host = host,
                    deviceClass = HomeLayoutDeviceClass.PHONE,
                    installedApps = emptyList(),
                    appIconLoader = SolidColorAppIconLoader(),
                    reducedMotion = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                )
            }
        }
    }

    private fun assertNoPageStaysLoading() {
        StreamSection.entries.forEach { section ->
            composeRule.onNodeWithTag(streamSpineMarkerTestTag(section.storedId)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithText("Loading").fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun everySectionLeavesLoadingWhenEveryAccessIsGranted() {
        show(host(SourceAccess.GRANTED, NotificationAccessStatus.GRANTED, SourceAccess.GRANTED))
        assertNoPageStaysLoading()
    }

    @Test
    fun everySectionLeavesLoadingWhenNoAccessIsGranted() {
        show(host(SourceAccess.REQUIRED, NotificationAccessStatus.NOT_GRANTED, SourceAccess.REQUIRED))
        assertNoPageStaysLoading()
    }

    @Test
    fun everySectionLeavesLoadingWhenTheCalendarIsUnavailable() {
        show(host(SourceAccess.UNAVAILABLE, NotificationAccessStatus.GRANTED, SourceAccess.REQUIRED))
        assertNoPageStaysLoading()
    }

    @Test
    fun aSourceThatNeverAnswersEndsAsUnavailableWithRetryAndRetryReadsAgain() {
        val release = java.util.concurrent.CountDownLatch(1)
        // The shared reader thread is stuck on something that never returns (until the test lets it).
        executor.execute { release.await() }
        show(
            host(
                SourceAccess.GRANTED,
                NotificationAccessStatus.GRANTED,
                SourceAccess.GRANTED,
                loadingTimeoutMillis = 300,
            ),
        )
        composeRule.onNodeWithTag(streamSpineMarkerTestTag(StreamSection.NOTIFICATIONS.storedId)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("This can't be shown right now.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Retry").onFirst().assertExists()
        release.countDown()
        composeRule.onAllNodesWithText("Retry").onFirst().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("This can't be shown right now.").fetchSemanticsNodes().isEmpty()
        }
    }
}
