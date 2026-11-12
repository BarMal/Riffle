package com.riffle.app.launcher.stream

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Base64

/** The Stream's image resolver: notification pictures and article lead images draw, always off the main thread. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamImageLoaderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val personal = AppProfileId("personal")

    private fun png(): ByteArray =
        ByteArrayOutputStream().use { out ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

    private fun notification(
        key: String,
        icon: ByteArray? = null,
    ) = LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = AppPackageName("chat"),
        profileId = personal,
        largeIconPngBase64 = icon?.let { Base64.getEncoder().encodeToString(it) },
        postedAtEpochMillis = 1,
    )

    @Test
    fun aNotificationsPictureIsFoundByItsOwnHandle() {
        val bytes = png()
        val notifications = listOf(notification("0|chat|1"), notification("0|chat|2", bytes))

        val found = notifications.largeIconBytesFor(ItemImageKeys.notificationArtwork(personal, "0|chat|2"))

        assertTrue(bytes.contentEquals(found))
    }

    @Test
    fun aNotificationThatWentAwayOrHadNoPictureHasNone() {
        val notifications = listOf(notification("0|chat|1"), notification("0|chat|2", png()))

        assertNull(notifications.largeIconBytesFor(ItemImageKeys.notificationArtwork(personal, "0|chat|1")))
        assertNull(notifications.largeIconBytesFor(ItemImageKeys.notificationArtwork(personal, "0|chat|9")))
        assertNull(
            listOf(
                notification("k", png()),
            ).largeIconBytesFor(ItemImageKeys.notificationArtwork(AppProfileId("work"), "k")),
        )
    }

    @Test
    fun aNotificationPictureLoadsOffTheMainThread() {
        val threads = mutableListOf<Thread>()
        val bytes = png()
        val loader =
            AndroidStreamImageLoader(
                context.packageManager,
                notificationArtwork = {
                    threads += Thread.currentThread()
                    bytes
                },
            )

        val image = runBlocking { loader.load(ItemImageKeys.notificationArtwork(personal, "0|chat|2"), 64) }

        assertNotNull(image)
        assertEquals(1, threads.size)
        assertTrue(threads.single() !== Looper.getMainLooper().thread)
    }

    @Test
    fun anArticlesLeadImageComesFromTheOfflineCacheOffTheMainThread() {
        val threads = mutableListOf<Thread>()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImageBitmap()
        val requested = mutableListOf<String>()
        val loader =
            AndroidStreamImageLoader(
                context.packageManager,
                feedArtwork = { digest ->
                    threads += Thread.currentThread()
                    requested += digest
                    bitmap
                },
            )

        val image = runBlocking { loader.load(ItemImageKeys.feedArtwork("abc123"), 64) }

        assertEquals(bitmap, image)
        assertEquals(listOf("abc123"), requested)
        assertTrue(threads.single() !== Looper.getMainLooper().thread)
    }

    @Test
    fun anImageThatCannotBeResolvedReadsAsNone() {
        val loader = AndroidStreamImageLoader(context.packageManager)

        assertNull(runBlocking { loader.load(ItemImageKeys.notificationArtwork(personal, "0|chat|1"), 64) })
        assertNull(runBlocking { loader.load(ItemImageKeys.feedArtwork("missing"), 64) })
        assertNull(runBlocking { loader.load(ItemImageHandle("unknown:thing"), 64) })
    }

    @Test
    fun aFailingResolverReadsAsNoneRatherThanCrashing() {
        val loader =
            AndroidStreamImageLoader(
                context.packageManager,
                notificationArtwork = { error("decode") },
                feedArtwork = { error("cache") },
            )

        assertNull(runBlocking { loader.load(ItemImageKeys.notificationArtwork(personal, "k"), 64) })
        assertNull(runBlocking { loader.load(ItemImageKeys.feedArtwork("d"), 64) })
    }
}
