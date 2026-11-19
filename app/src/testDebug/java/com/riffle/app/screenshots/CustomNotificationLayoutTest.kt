package com.riffle.app.screenshots

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.R
import com.riffle.app.launcher.notifications.NotificationCustomContentStore
import com.riffle.app.launcher.notifications.RemoteViewsReader
import com.riffle.app.launcher.stream.ContentEntry
import com.riffle.app.launcher.stream.LocalNotificationEmbeds
import com.riffle.app.launcher.stream.NotificationEmbeds
import com.riffle.app.launcher.stream.OpenStreamCard
import com.riffle.app.launcher.stream.STREAM_EMBEDDED_LAYOUT_TAG
import com.riffle.app.launcher.stream.StackEntryCard
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.stream.CustomNotificationContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A notification that draws itself with a layout of its own and fills in no title or text (device fixes, round 6, item
 * 2), with real `RemoteViews` built from layouts of this (debug) app: the layout is inflated off-screen and read into a
 * title, lines and a picture; a layout with no text is embedded; a notification posted again is read again, held to
 * twice a second; a removed one leaves nothing; a layout that cannot be inflated fails quietly; and the cards draw what
 * was read in their own style, or the app's layout, and neither when the content is hidden.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class CustomNotificationLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val profile = AppProfile.personal().id

    /** A scheduler the test steps: a read runs only when the clock reaches it. */
    private class TestScheduler : NotificationCustomContentStore.Scheduler {
        var clock = 0L
        private val queue = ArrayList<Pair<Long, Runnable>>()

        override fun now(): Long = clock

        override fun postDelayed(
            delayMillis: Long,
            task: Runnable,
        ) {
            queue += (clock + delayMillis) to task
        }

        override fun cancel(task: Runnable) {
            queue.removeAll { it.second === task }
        }

        fun advanceBy(millis: Long) {
            clock += millis
            while (true) {
                val due = queue.filter { it.first <= clock }.minByOrNull { it.first } ?: break
                queue.remove(due)
                due.second.run()
            }
        }

        val waiting: Int get() = queue.size
    }

    private val scheduler = TestScheduler()
    private var reads = 0
    private var changes = 0
    private lateinit var stopObserving: () -> Unit

    @Before
    fun setUp() {
        NotificationCustomContentStore.scheduler = scheduler
        NotificationCustomContentStore.reader = { context, views ->
            reads += 1
            RemoteViewsReader.read(context, views)
        }
        stopObserving = NotificationCustomContentStore.observe { changes += 1 }
    }

    @After
    fun tearDown() {
        stopObserving()
        NotificationCustomContentStore.clear()
    }

    private fun trip(
        title: String = "Get off at Angel",
        lineOne: String = "3 stops away",
        lineTwo: String = "Northern line",
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.test_notification_custom).apply {
            setTextViewText(R.id.test_notification_title, title)
            setTextViewText(R.id.test_notification_line_one, lineOne)
            setTextViewText(R.id.test_notification_line_two, lineTwo)
            setTextViewText(R.id.test_notification_gone, "NOT SHOWN")
            setTextViewText(R.id.test_notification_button, "Next")
            setOnClickPendingIntent(
                R.id.test_notification_button,
                android.app.PendingIntent.getBroadcast(
                    context,
                    1,
                    android.content.Intent("next"),
                    android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            setImageViewBitmap(R.id.test_notification_image, Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888))
        }

    private fun map(): RemoteViews =
        RemoteViews(context.packageName, R.layout.test_notification_no_text).apply {
            setImageViewBitmap(
                R.id.test_notification_map,
                Bitmap.createBitmap(300, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(MAP_GREEN) },
            )
        }

    private fun posted(
        compact: RemoteViews?,
        big: RemoteViews? = null,
        title: String? = null,
        id: Int = 7,
    ): StatusBarNotification {
        val builder = Notification.Builder(context, "channel").setSmallIcon(android.R.drawable.ic_menu_send)
        compact?.let(builder::setCustomContentView)
        big?.let(builder::setCustomBigContentView)
        title?.let(builder::setContentTitle)
        return StatusBarNotification(
            "com.example.nav", "com.example.nav", id, null, 1000, 1000, 0, builder.build(), Process.myUserHandle(), 1L,
        )
    }

    private fun post(notification: StatusBarNotification) =
        NotificationCustomContentStore.post(
            context,
            notification,
            profile,
        )

    private fun contentOf(notification: StatusBarNotification): CustomNotificationContent? =
        NotificationCustomContentStore.contentFor(LauncherNotificationKey(notification.key))

    // Reading ---------------------------------------------------------------------------------------------------------

    @Test
    fun theLayoutIsReadIntoATitleItsLinesAndItsPicture() {
        val read = RemoteViewsReader.read(context, trip())
        assertNotNull(read)
        // In drawing order; the text of a view that is not shown is not read.
        assertEquals(listOf("3 stops away", "Get off at Angel", "Northern line", "Next"), read!!.texts.map { it.text })
        val title = read.texts.single { it.text == "Get off at Angel" }
        assertEquals(20f, title.sizeSp, 0.5f)
        assertTrue(title.bold)
        assertTrue("the button is clickable", read.texts.single { it.text == "Next" }.clickable)
        assertEquals(1, read.clickableCount)
        // The picture is sized down to the store's bound, keeping its proportions.
        assertEquals(RemoteViewsReader.MAX_PICTURE_EDGE_PX, read.picture?.width)
        assertEquals(RemoteViewsReader.MAX_PICTURE_EDGE_PX * 3 / 4, read.picture?.height)
    }

    @Test
    fun aPostedNotificationWithItsOwnLayoutBecomesTypedContentAfterTheRead() {
        val notification = posted(trip(), big = trip(lineTwo = "Arrive 14:32"))
        post(notification)
        // The listener is not kept waiting: nothing is read until the main thread runs the read.
        assertNull(contentOf(notification))
        assertEquals(0, reads)
        scheduler.advanceBy(0)
        val content = contentOf(notification)!!
        assertEquals("Get off at Angel", content.title)
        assertEquals(listOf("3 stops away", "Northern line"), content.lines)
        assertEquals(listOf("3 stops away", "Arrive 14:32"), content.bigLines)
        assertEquals(false, content.embed)
        assertEquals(1, changes)
        // The picture is in memory under the handle the content names.
        val handle = content.thumbnail!!.key
        assertTrue(NotificationCustomContentStore.holds(handle))
        assertNotNull(NotificationCustomContentStore.bitmapFor(handle))
    }

    @Test
    fun aNotificationThatFillsInItsTitleIsNotRead() {
        val notification = posted(trip(), title = "Trip to Angel")
        post(notification)
        scheduler.advanceBy(1_000)
        assertNull(contentOf(notification))
        assertEquals(0, reads)
        assertNull(NotificationCustomContentStore.viewsFor(notification.key, big = false))
    }

    @Test
    fun aLayoutWithNoTextIsEmbedded() {
        val notification = posted(map())
        post(notification)
        scheduler.advanceBy(0)
        val content = contentOf(notification)!!
        assertNull(content.title)
        assertEquals(true, content.embed)
        assertNotNull(NotificationCustomContentStore.viewsFor(notification.key, big = false))
        // With no expanded layout the open card embeds the compact one.
        assertNotNull(NotificationCustomContentStore.viewsFor(notification.key, big = true))
    }

    @Test
    fun aNotificationPostedAgainIsReadAgainAndTheCardFollows() {
        post(posted(trip()))
        scheduler.advanceBy(0)
        scheduler.advanceBy(1_000)
        val again = posted(trip(lineOne = "2 stops away"))
        post(again)
        scheduler.advanceBy(0)
        assertEquals(listOf("2 stops away", "Northern line"), contentOf(again)!!.lines)
        assertEquals(2, contentOf(again)!!.revision)
        assertEquals(2, changes)
    }

    @Test
    fun aNotificationThatUpdatesManyTimesASecondIsReadAtMostTwiceASecond() {
        post(posted(trip()))
        scheduler.advanceBy(0)
        assertEquals(1, reads)
        // Ten updates in 100 ms: one read is waiting, no more.
        repeat(10) { index ->
            scheduler.advanceBy(10)
            post(posted(trip(lineOne = "${10 - index} stops away")))
        }
        assertEquals(1, reads)
        assertEquals(1, scheduler.waiting)
        scheduler.advanceBy(NotificationCustomContentStore.MIN_INTERVAL_MILLIS)
        assertEquals(2, reads)
        // It read the newest layout.
        assertEquals("1 stops away", contentOf(posted(trip()))!!.lines.first())
    }

    @Test
    fun postingTheSameWordsAgainTellsNobody() {
        post(posted(trip()))
        scheduler.advanceBy(0)
        scheduler.advanceBy(1_000)
        post(posted(trip()))
        scheduler.advanceBy(0)
        assertEquals(2, reads)
        assertEquals(1, changes)
    }

    @Test
    fun aRemovedNotificationLeavesNothingAndAWaitingReadIsDropped() {
        val notification = posted(trip())
        post(notification)
        scheduler.advanceBy(0)
        val handle = contentOf(notification)!!.thumbnail!!.key
        post(notification)
        assertEquals(1, scheduler.waiting)
        NotificationCustomContentStore.remove(notification.key)
        assertNull(contentOf(notification))
        assertNull(NotificationCustomContentStore.bitmapFor(handle))
        assertNull(NotificationCustomContentStore.viewsFor(notification.key, big = false))
        assertEquals(0, scheduler.waiting)
        scheduler.advanceBy(1_000)
        assertEquals(1, reads)
        // And when the listener's list no longer has it.
        post(notification)
        scheduler.advanceBy(1_000)
        NotificationCustomContentStore.retain(emptySet())
        assertNull(contentOf(notification))
    }

    @Test
    fun aLayoutThatCannotBeInflatedFailsQuietly() {
        // A layout id that does not exist in the posting app: the platform throws while inflating.
        val broken = RemoteViews(context.packageName, 0x7f0f_ffff)
        assertNull(RemoteViewsReader.read(context, broken))
        val notification = posted(broken)
        post(notification)
        scheduler.advanceBy(0)
        val content = contentOf(notification)!!
        // Nothing read, and nothing to embed: the card is the app's name and no more.
        assertEquals(CustomNotificationContent(revision = 1), content)
        // A reader that throws is contained too.
        NotificationCustomContentStore.reader = { _, _ -> error("boom") }
        scheduler.advanceBy(1_000)
        post(notification)
        scheduler.advanceBy(0)
        assertEquals(false, contentOf(notification)!!.embed)
    }

    // The cards -------------------------------------------------------------------------------------------------------

    private fun notification(custom: CustomNotificationContent?): NotificationContent =
        StreamFixtures.mailNotification.copy(
            id = StreamContentId("notifications:nav:1"),
            appLabel = "Navigator",
            title = null,
            text = null,
            notificationKey = "nav-key",
            custom = custom,
        )

    private fun showBrowse(
        content: NotificationContent,
        views: RemoteViews? = null,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalNotificationEmbeds provides NotificationEmbeds { _, _ -> views }) {
                    Box(Modifier.padding(16.dp)) {
                        val entry = ContentEntry(content)
                        StackEntryCard(
                            entry = entry,
                            env = StreamFixtures.environment(reducedMotion = true),
                            callbacks = StreamCallbacks(),
                            groupKey = null,
                            onOpen = {},
                            modifier = Modifier.size(288.dp, entry.height),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val read =
        CustomNotificationContent(
            title = "Get off at Angel",
            lines = listOf("3 stops away", "Northern line"),
            bigLines = listOf("3 stops away", "Northern line", "Arrive 14:32"),
            revision = 1,
        )

    @Test
    fun theBrowseCardDrawsWhatWasReadInItsOwnStyle() {
        showBrowse(notification(read))
        composeRule.onNodeWithText("Get off at Angel").assertIsDisplayed()
        composeRule.onNodeWithText("3 stops away").assertIsDisplayed()
        composeRule.onNodeWithText("Northern line").assertIsDisplayed()
        // The app's name is the second line, and nothing says the content is hidden.
        composeRule.onNodeWithText("Navigator · 55 min. ago", substring = false).assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("Hidden content").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(STREAM_EMBEDDED_LAYOUT_TAG).fetchSemanticsNodes().size)
        composeRule.captureScreen()
    }

    @Test
    fun aLayoutWithNoTextIsEmbeddedInTheCardUnderTheAppsName() {
        showBrowse(notification(CustomNotificationContent(embed = true, revision = 1)), views = map())
        composeRule.onNodeWithText("Navigator").assertIsDisplayed()
        composeRule.onNodeWithTag(STREAM_EMBEDDED_LAYOUT_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Layout from Navigator").assertIsDisplayed()
        // Sized to the card's content width, and no taller than the cap.
        val bounds = composeRule.onNodeWithTag(STREAM_EMBEDDED_LAYOUT_TAG).fetchSemanticsNode().size
        val density = composeRule.density.density
        assertTrue("capped height: ${bounds.height / density} dp", bounds.height / density <= 96.5f)
        assertTrue("as wide as the card's content: ${bounds.width / density} dp", bounds.width / density > 200f)
        composeRule.captureScreen()
    }

    @Test
    fun aHiddenNotificationShowsTheHiddenStateAndNeitherLinesNorTheLayout() {
        val hidden =
            notification(null).copy(appLabel = "Navigator", privacy = StreamPrivacy.REDACTED, notificationKey = null)
        showBrowse(hidden, views = map())
        composeRule.onNodeWithText("Hidden content").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag(STREAM_EMBEDDED_LAYOUT_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun theOpenCardDrawsTheExpandedLayoutsLines() {
        composeRule.setContent {
            ScreenshotBackdrop {
                OpenStreamCard(
                    content = notification(read),
                    env = StreamFixtures.environment(reducedMotion = true),
                    callbacks = StreamCallbacks(),
                    groupKey = null,
                    actionsOnCard = true,
                    onClose = {},
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Get off at Angel").assertIsDisplayed()
        composeRule.onNodeWithText("Arrive 14:32").assertIsDisplayed()
    }

    @Test
    fun anEmbeddedLayoutIsAppliedAgainInPlaceWhenTheNotificationIsPostedAgain() {
        val host = com.riffle.app.launcher.stream.EmbeddedLayoutHost(context)
        host.show(map(), revision = 1)
        assertEquals(1, host.childCount)
        assertEquals(1, host.applied)
        // The same revision is not applied twice.
        host.show(map(), revision = 1)
        assertEquals(1, host.applied + host.reapplied)
        // A new post of the same layout is applied in place; another layout is inflated afresh.
        host.show(map(), revision = 2)
        assertEquals(1, host.reapplied)
        host.show(trip(), revision = 3)
        assertEquals(2, host.applied)
        assertEquals(1, host.childCount)
        // A layout that fails leaves nothing, and no crash.
        host.show(RemoteViews(context.packageName, 0x7f0f_ffff), revision = 4)
        assertEquals(0, host.childCount)
        host.show(null, revision = 5)
        assertEquals(0, host.childCount)
    }

    private companion object {
        const val MAP_GREEN = 0xFF2E7D32.toInt()
    }
}
