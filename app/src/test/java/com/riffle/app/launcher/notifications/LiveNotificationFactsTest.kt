package com.riffle.app.launcher.notifications

import android.app.Notification
import android.content.Context
import android.os.Process
import android.service.notification.StatusBarNotification
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.stream.LiveNotificationRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the listener reads of a platform notification for the "live" rule (device fixes, round 6, item 3): ongoing, a
 * layout of its own, a progress bar and the category; and that the registry answers for a posted notification and no
 * longer once it has gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class LiveNotificationFactsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun clear() = AndroidNotificationStageActionGateway.clear()

    private fun builder() = Notification.Builder(context, "channel").setSmallIcon(android.R.drawable.ic_menu_send)

    @Test
    fun aTripWithItsOwnLayoutIsOngoingWithCustomViews() {
        val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        val facts =
            liveFactsOf(
                builder().setOngoing(true).setCustomContentView(views).setStyle(Notification.DecoratedCustomViewStyle())
                    .build(),
            )
        assertEquals(LiveNotificationRule.Facts(ongoing = true, customViews = true), facts)
    }

    @Test
    fun aDownloadShowsProgressAndANavigationSaysSo() {
        assertEquals(
            LiveNotificationRule.Facts(ongoing = true, progress = true, category = NotificationCategory.PROGRESS),
            liveFactsOf(
                builder().setOngoing(
                    true,
                ).setProgress(100, 40, false).setCategory(Notification.CATEGORY_PROGRESS).build(),
            ),
        )
        assertEquals(true, liveFactsOf(builder().setOngoing(true).setProgress(0, 0, true).build()).progress)
        assertEquals(
            NotificationCategory.NAVIGATION,
            liveFactsOf(builder().setCategory(Notification.CATEGORY_NAVIGATION).build()).category,
        )
    }

    @Test
    fun aPlainNotificationHasNoneOfIt() {
        assertEquals(LiveNotificationRule.Facts(ongoing = false), liveFactsOf(builder().setContentTitle("Hi").build()))
    }

    @Test
    fun theRegistryAnswersWhileTheNotificationIsPostedAndNotAfter() {
        val posted =
            StatusBarNotification(
                "com.example.nav", "com.example.nav", 1, null, 1000, 1000, 0,
                builder().setOngoing(true).setCategory(Notification.CATEGORY_NAVIGATION).build(),
                Process.myUserHandle(), 1L,
            )
        val key = LauncherNotificationKey(posted.key)
        AndroidNotificationStageActionGateway.replace(context, posted)
        assertEquals(true, AndroidNotificationStageActionGateway.isLive(key))
        AndroidNotificationStageActionGateway.remove(posted.key)
        assertEquals(false, AndroidNotificationStageActionGateway.isLive(key))
    }
}
