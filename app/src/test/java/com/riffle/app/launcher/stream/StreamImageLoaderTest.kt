package com.riffle.app.launcher.stream

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
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
    fun aContactPhotoAndAPictureComeFromMemoryOffTheMainThreadAndAreSizedDownToWhatIsDrawn() {
        val threads = mutableListOf<Thread>()
        val big = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        val loader =
            AndroidStreamImageLoader(
                context.packageManager,
                notificationBitmap = { key ->
                    threads += Thread.currentThread()
                    big.takeIf { key.startsWith("notification-picture:") || key.startsWith("notification-person:") }
                },
            )

        val picture = runBlocking { loader.load(ItemImageKeys.notificationPicture(personal, "k"), 128) }!!
        val person = runBlocking { loader.load(ItemImageKeys.notificationPerson(personal, "k"), 64) }!!

        assertEquals(128, picture.width)
        assertEquals(64, picture.height)
        assertEquals(64, person.width)
        assertTrue(threads.all { it !== Looper.getMainLooper().thread })
        // The source is never drawn larger than it is.
        assertEquals(512, runBlocking { loader.load(ItemImageKeys.notificationPicture(personal, "k"), 1024) }!!.width)
    }

    @Test
    fun aContactPhotoOrPictureThatHasGoneReadsAsNone() {
        val loader = AndroidStreamImageLoader(context.packageManager, notificationBitmap = { null })

        assertNull(runBlocking { loader.load(ItemImageKeys.notificationPerson(personal, "k"), 64) })
        assertNull(runBlocking { loader.load(ItemImageKeys.notificationPicture(personal, "k"), 64) })
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

    private val files = AppIdentity(AppPackageName("com.files"), AppActivityName(".Main"))

    @Test
    fun aShortcutsIconComesFromItsResolverOffTheMainThread() {
        val threads = mutableListOf<Thread>()
        val handles = mutableListOf<ItemImageHandle>()
        val loader =
            AndroidStreamImageLoader(
                context.packageManager,
                shortcutIcon = { handle ->
                    threads += Thread.currentThread()
                    handles += handle
                    ColorDrawable(Color.GREEN)
                },
            )
        val handle = ItemImageKeys.shortcutIcon(files, "recent")

        assertNotNull(runBlocking { loader.load(handle, 64) })
        assertEquals(listOf(handle), handles)
        assertTrue(threads.single() !== Looper.getMainLooper().thread)
    }

    @Test
    fun aShortcutTheLauncherCannotReadDrawsItsAppsIconInstead() {
        val loader = AndroidStreamImageLoader(context.packageManager, shortcutIcon = { null })
        val handle = ItemImageKeys.shortcutIcon(files, "recent")
        // An app that is not installed has no icon to fall back to: the card draws without one.
        assertNull(runBlocking { loader.load(handle, 64) })

        val installed = android.content.pm.PackageInfo().apply { packageName = "com.files" }
        installed.applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.files" }
        val shadow = org.robolectric.Shadows.shadowOf(context.packageManager)
        shadow.installPackage(installed)
        shadow.setApplicationIcon("com.files", ColorDrawable(Color.BLUE))
        assertNotNull(runBlocking { loader.load(handle, 64) })
    }
}
